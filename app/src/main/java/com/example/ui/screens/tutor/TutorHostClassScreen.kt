package com.example.ui.screens.tutor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.student.defaultTextFieldColors
import com.example.ui.theme.*

@Composable
fun TutorHostClassScreen(
    onStartClass: (title: String, fee: Double, subject: String, scheduledDate: String, scheduledTime: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var className by remember { mutableStateOf("Calculus & Analytical Geometry") }
    var subject by remember { mutableStateOf("Mathematics") }
    var isFreeClass by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("3000") }

    var selectedDate by remember { mutableStateOf("Today") }
    var customDateText by remember { mutableStateOf("") }
    var isCustomDate by remember { mutableStateOf(false) }

    var selectedTime by remember { mutableStateOf("Starting Now") }
    var customTimeText by remember { mutableStateOf("") }
    var isCustomTime by remember { mutableStateOf(false) }

    var durationMinutes by remember { mutableStateOf(60) }

    var generatedLink by remember { mutableStateOf<String?>(null) }

    val dateOptions = listOf("Today", "Tomorrow", "Pick Date")
    val timeOptions = listOf("Starting Now", "10:00 AM", "2:00 PM", "4:00 PM", "6:00 PM", "Pick Time")
    val durationOptions = listOf(30, 45, 60, 90)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlatyDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Host a Live Class",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Create an instant HD interactive video classroom. Host for free or set a ticket fee, choose your scheduled date and time, and share your link with students.",
            color = SlatyTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1F1C2F),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D48)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Class details
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class / Topic Name") },
                    modifier = Modifier.fillMaxWidth().testTag("host_class_name_input"),
                    colors = defaultTextFieldColors()
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Mathematics, Physics)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = defaultTextFieldColors()
                )

                // Free vs Paid Class Selector
                Text(
                    text = "Pricing Option",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Free Class button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFreeClass) SlatyMint.copy(alpha = 0.2f) else Color(0xFF28233D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isFreeClass) SlatyMint else Color(0xFF3B3454)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isFreeClass = true }
                            .testTag("pricing_free_class_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isFreeClass) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isFreeClass) SlatyMint else SlatyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Free Class",
                                color = if (isFreeClass) SlatyMint else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Paid Class button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isFreeClass) SlatyPrimary.copy(alpha = 0.2f) else Color(0xFF28233D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isFreeClass) SlatyPrimary else Color(0xFF3B3454)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isFreeClass = false }
                            .testTag("pricing_paid_class_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (!isFreeClass) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (!isFreeClass) SlatyPrimary else SlatyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paid Ticket",
                                color = if (!isFreeClass) SlatyPrimary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (isFreeClass) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SlatyMint.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlatyMint.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = SlatyMint, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This class is completely free for all students to join without fee deduction.",
                                color = SlatyMint,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Ticket Fee per Student (₦)") },
                        modifier = Modifier.fillMaxWidth().testTag("host_class_fee_input"),
                        colors = defaultTextFieldColors()
                    )
                }

                HorizontalDivider(color = Color(0xFF332D48), thickness = 1.dp)

                // Date Selection
                Text(
                    text = "Scheduled Date",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dateOptions.forEach { opt ->
                        val isSelected = if (opt == "Pick Date") isCustomDate else (!isCustomDate && selectedDate == opt)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SlatyPrimary.copy(alpha = 0.25f) else Color(0xFF28233D),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SlatyPrimary else Color(0xFF3B3454)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (opt == "Pick Date") {
                                        isCustomDate = true
                                    } else {
                                        isCustomDate = false
                                        selectedDate = opt
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = opt,
                                    color = if (isSelected) Color.White else SlatyTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (isCustomDate) {
                    OutlinedTextField(
                        value = customDateText,
                        onValueChange = { customDateText = it },
                        label = { Text("Enter Date (e.g. Nov 14, 2026)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_date_input"),
                        colors = defaultTextFieldColors()
                    )
                }

                // Time Selection
                Text(
                    text = "Scheduled Time",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val row1 = timeOptions.take(3)
                    val row2 = timeOptions.drop(3)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row1.forEach { opt ->
                            val isSelected = !isCustomTime && selectedTime == opt
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SlatyPrimary.copy(alpha = 0.25f) else Color(0xFF28233D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SlatyPrimary else Color(0xFF3B3454)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isCustomTime = false
                                        selectedTime = opt
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = opt,
                                        color = if (isSelected) Color.White else SlatyTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row2.forEach { opt ->
                            val isSelected = if (opt == "Pick Time") isCustomTime else (!isCustomTime && selectedTime == opt)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SlatyPrimary.copy(alpha = 0.25f) else Color(0xFF28233D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SlatyPrimary else Color(0xFF3B3454)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (opt == "Pick Time") {
                                            isCustomTime = true
                                        } else {
                                            isCustomTime = false
                                            selectedTime = opt
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = opt,
                                        color = if (isSelected) Color.White else SlatyTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                if (isCustomTime) {
                    OutlinedTextField(
                        value = customTimeText,
                        onValueChange = { customTimeText = it },
                        label = { Text("Enter Time (e.g. 5:30 PM)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_time_input"),
                        colors = defaultTextFieldColors()
                    )
                }

                // Duration Selection
                Text(
                    text = "Session Duration",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationOptions.forEach { mins ->
                        val isSelected = durationMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SlatyPrimary.copy(alpha = 0.25f) else Color(0xFF28233D),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SlatyPrimary else Color(0xFF3B3454)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { durationMinutes = mins }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${mins}m",
                                    color = if (isSelected) Color.White else SlatyTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Generate Link Button
                Button(
                    onClick = {
                        val code = com.example.data.repository.LiveClassRepository.normalizeRoomCode(className)
                        generatedLink = code
                        Toast.makeText(context, "Live classroom code generated: $code", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2945)),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_link_button")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = SlatyMint)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Shareable Link", color = SlatyMint, fontWeight = FontWeight.Bold)
                }

                // Start Class (Tutor)
                Button(
                    onClick = {
                        val fee = if (isFreeClass) 0.0 else (amountText.toDoubleOrNull() ?: 2500.0)
                        val finalDate = if (isCustomDate && customDateText.isNotBlank()) customDateText.trim() else selectedDate
                        val finalTime = if (isCustomTime && customTimeText.isNotBlank()) customTimeText.trim() else selectedTime
                        onStartClass(className.trim(), fee, subject.trim(), finalDate, finalTime, durationMinutes)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFreeClass) SlatyMint else SlatyPrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_class_tutor_button")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = if (isFreeClass) Color.Black else Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFreeClass) "Launch Free Live Class" else "Launch Live Class (Ticket: ₦$amountText)",
                        color = if (isFreeClass) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Generated link view
        if (generatedLink != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151320),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlatyMint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Shareable Class Link:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = generatedLink ?: "",
                        color = SlatyMint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Class Link", generatedLink)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Link", color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Ready to share with students!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D48)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
