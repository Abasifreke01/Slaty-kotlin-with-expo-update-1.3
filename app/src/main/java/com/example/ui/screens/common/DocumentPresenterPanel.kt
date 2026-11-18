package com.example.ui.screens.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app Study Material & Document Presenter for tutors.
 * Supports multi-page PDF documents (via Android PdfRenderer) and high-res images
 * selected through the zero-permission Android Storage Access Framework.
 */
@Composable
fun DocumentPresenterPanel(
    documentUri: Uri,
    isScreenSharingLive: Boolean,
    onChangeDocument: () -> Unit,
    onClosePresenter: () -> Unit,
    onRequestLiveScreenShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val documentTitle = remember(documentUri) { resolveDocumentDisplayName(context, documentUri) }
    val mimeType = remember(documentUri) { context.contentResolver.getType(documentUri) ?: "" }
    val isPdf = remember(mimeType, documentUri) {
        mimeType.contains("pdf", ignoreCase = true) ||
            documentUri.toString().lowercase().endsWith(".pdf")
    }

    var pageCount by remember(documentUri) { mutableIntStateOf(1) }
    var currentPage by remember(documentUri) { mutableIntStateOf(0) }
    var renderedBitmap by remember(documentUri) { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember(documentUri) { mutableStateOf<String?>(null) }
    var isLoading by remember(documentUri) { mutableStateOf(true) }

    // Zoom & Pan state for formulas/diagrams
    var scale by remember(documentUri, currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(documentUri, currentPage) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 3.5f)
        scale = nextScale
        offset = if (nextScale > 1f) offset + panChange else Offset.Zero
    }

    // Load PDF page or Image asynchronously off the UI thread
    LaunchedEffect(documentUri, currentPage, isPdf) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            try {
                if (isPdf) {
                    var pfd: ParcelFileDescriptor? = null
                    var renderer: PdfRenderer? = null
                    try {
                        pfd = context.contentResolver.openFileDescriptor(documentUri, "r")
                            ?: return@withContext Result.failure<Pair<Bitmap, Int>>(IllegalStateException("Could not open PDF file"))
                        renderer = PdfRenderer(pfd)
                        val total = renderer.pageCount.coerceAtLeast(1)
                        val safeIndex = currentPage.coerceIn(0, total - 1)
                        val page = renderer.openPage(safeIndex)
                        val targetWidth = 1440
                        val aspectHeight = ((targetWidth.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(targetWidth, aspectHeight, Bitmap.Config.ARGB_8888)
                        // Draw solid white background so transparent PDFs remain crisp
                        Canvas(bmp).drawColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        Result.success(bmp to total)
                    } finally {
                        try { renderer?.close() } catch (_: Throwable) {}
                        try { pfd?.close() } catch (_: Throwable) {}
                    }
                } else {
                    val bmp = context.contentResolver.openInputStream(documentUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: return@withContext Result.failure<Pair<Bitmap, Int>>(IllegalStateException("Could not decode image"))
                    Result.success(bmp to 1)
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

        result.onSuccess { (bmp, total) ->
            renderedBitmap = bmp
            pageCount = total
            isLoading = false
        }.onFailure { err ->
            errorMessage = err.localizedMessage ?: "Unable to preview this study material"
            isLoading = false
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0E0C18),
        border = BorderStroke(1.5.dp, if (isScreenSharingLive) SlatyMint else SlatyPrimary),
        modifier = modifier.testTag("document_presenter_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Presenter Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF171426))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Presented Document",
                        tint = SlatyMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = documentTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isScreenSharingLive) "LIVE TO STUDENTS" else "PREVIEW MODE (Tap Broadcast Screen)",
                            color = if (isScreenSharingLive) SlatyMint else Color(0xFFFFB74D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isScreenSharingLive) {
                        Button(
                            onClick = onRequestLiveScreenShare,
                            colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp).testTag("broadcast_document_button")
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Go Live", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    IconButton(
                        onClick = onChangeDocument,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Change Document", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onClosePresenter,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Document", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Document Canvas Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0A0912))
                    .clip(RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = SlatyMint)
                    }
                    errorMessage != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage ?: "Failed to load file", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    renderedBitmap != null -> {
                        Image(
                            bitmap = renderedBitmap!!.asImageBitmap(),
                            contentDescription = "Presented Study Material Page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .transformable(state = transformState)
                        )

                        if (scale > 1.05f) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xCC161326),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .clickable {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                            ) {
                                Text(
                                    text = "Reset Zoom",
                                    color = SlatyMint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Pagination Controls (PDFs & multi-page study packs)
            if (isPdf || pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171426))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (currentPage > 0) Color(0xFF2B2544) else Color(0xFF1D192E),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = currentPage > 0) {
                                if (currentPage > 0) currentPage -= 1
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 0) Color.White else Color.Gray
                            )
                        }
                    }

                    Text(
                        text = "Page ${currentPage + 1} of $pageCount",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (currentPage < pageCount - 1) Color(0xFF2B2544) else Color(0xFF1D192E),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = currentPage < pageCount - 1) {
                                if (currentPage < pageCount - 1) currentPage += 1
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Page",
                                tint = if (currentPage < pageCount - 1) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDocumentDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Throwable) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Study Material"
}
