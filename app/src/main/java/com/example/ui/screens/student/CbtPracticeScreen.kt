package com.example.ui.screens.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.CbtQuizState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CbtPracticeScreen(
    quizState: CbtQuizState,
    onBack: () -> Unit,
    onSelectAnswer: (questionIndex: Int, optionIndex: Int) -> Unit,
    onNavigateQuestion: (Int) -> Unit,
    onSubmit: () -> Unit,
    onRestart: () -> Unit,
    onSwitchSubject: (String) -> Unit,
    availableSubjects: List<com.example.model.CbtSubject> = emptyList(),
    modifier: Modifier = Modifier
) {
    val currentQ = quizState.questions.getOrNull(quizState.currentIndex)
    val minutes = quizState.secondsRemaining / 60
    val seconds = quizState.secondsRemaining % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)
    var showSubjectDialog by remember { mutableStateOf(false) }

    // Subject Selection Modal Dialog (Subjects from Firebase Database)
    if (showSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            containerColor = Color(0xFF1B1728),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = SlatyMint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Choose CBT Subject",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Subjects from Firebase Database. Questions are continuously shuffled on every drill:",
                        color = SlatyTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    val subjectsList = if (availableSubjects.isNotEmpty()) availableSubjects else listOf(
                        com.example.model.CbtSubject("biology", "Biology", "BIO-JAMB", "Cell biology, physiology & ecology", 40),
                        com.example.model.CbtSubject("mathematics", "Mathematics", "MTH-UTME", "Calculus, trigonometry & algebra", 40),
                        com.example.model.CbtSubject("physics", "Physics", "PHY-JAMB", "Mechanics, waves & electricity", 40),
                        com.example.model.CbtSubject("chemistry", "Chemistry", "CHM-JAMB", "Organic, physical & stoichiometry", 40),
                        com.example.model.CbtSubject("english", "English Language", "ENG-UTME", "Comprehension & structure", 40),
                        com.example.model.CbtSubject("economics", "Economics", "ECN-JAMB", "Micro & macro economics", 40),
                        com.example.model.CbtSubject("government", "Government", "GOV-JAMB", "Political systems & constitutions", 40)
                    )

                    subjectsList.forEach { subj ->
                        val isSelected = subj.name.equals(quizState.subject, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2E244B) else Color(0xFF141120),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) SlatyMint else Color(0xFF2C2640)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSubjectDialog = false
                                    onSwitchSubject(subj.name)
                                }
                                .testTag("select_subject_${subj.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) SlatyMint else Color(0xFF241F38)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else SlatyMint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = subj.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (subj.code.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2C2445)
                                            ) {
                                                Text(
                                                    text = subj.code,
                                                    color = SlatyLavender,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (subj.description.isNotBlank()) {
                                        Text(
                                            text = subj.description,
                                            color = SlatyTextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = SlatyMint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubjectDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${quizState.subject} CBT Drill",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlatyMint.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Shuffled",
                                    color = SlatyMint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (quizState.isFinished) "Test Finished" else "Time Remaining: $formattedTime",
                            color = if (quizState.secondsRemaining < 120 && !quizState.isFinished) SlatyError else SlatyMint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Open Subjects List Modal
                    Button(
                        onClick = { showSubjectDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282142)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("cbt_choose_subject_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint = SlatyMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Subjects",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlatyDarkBackground)
            )
        },
        containerColor = SlatyDarkBackground,
        bottomBar = {
            if (!quizState.isFinished) {
                Surface(
                    color = Color(0xFF151320),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateQuestion(quizState.currentIndex - 1) },
                            enabled = quizState.currentIndex > 0,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Previous")
                        }

                        Button(
                            onClick = onSubmit,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SlatyError),
                            modifier = Modifier.testTag("cbt_submit_button")
                        ) {
                            Text("Submit Test", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onNavigateQuestion(quizState.currentIndex + 1) },
                            enabled = quizState.currentIndex < quizState.questions.size - 1,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary)
                        ) {
                            Text("Next", color = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Result Banner if finished
            if (quizState.isFinished) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1F1C2F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlatyMint),
                    modifier = Modifier.fillMaxWidth().testTag("cbt_score_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val percentage = if (quizState.total > 0) (quizState.score * 100) / quizState.total else 0
                        Text("Exam Completed!", color = SlatyMint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${quizState.score} / ${quizState.total}",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Score: $percentage% accuracy",
                            color = if (percentage >= 70) SlatyMint else SlatyAccentYellow,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onRestart,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary)
                            ) {
                                Text("Retake Test", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Return Home")
                            }
                        }
                    }
                }
            }

            // Question Palette Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quizState.questions.forEachIndexed { idx, _ ->
                    val isCurrent = idx == quizState.currentIndex
                    val isAnswered = quizState.userAnswers.containsKey(idx)
                    val bg = when {
                        isCurrent -> SlatyMint
                        isAnswered -> Color(0xFF2E5E55)
                        else -> Color(0xFF201C2E)
                    }
                    val textColor = if (isCurrent) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .clickable { onNavigateQuestion(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Question Card
            if (currentQ != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F1C2F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D48)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Question ${quizState.currentIndex + 1} of ${quizState.questions.size}",
                                color = SlatyMint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = quizState.subject,
                                color = SlatyTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentQ.question,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Options list
                currentQ.options.forEachIndexed { optIndex, optText ->
                    val isSelected = quizState.userAnswers[quizState.currentIndex] == optIndex
                    val isCorrect = optIndex == currentQ.correctIndex

                    val borderColor = when {
                        quizState.isFinished && isCorrect -> SlatyMint
                        quizState.isFinished && isSelected && !isCorrect -> SlatyError
                        isSelected -> SlatyMint
                        else -> Color(0xFF332D48)
                    }

                    val containerColor = when {
                        quizState.isFinished && isCorrect -> Color(0xFF123D34)
                        quizState.isFinished && isSelected && !isCorrect -> Color(0xFF42161A)
                        isSelected -> Color(0xFF1F3832)
                        else -> Color(0xFF1F1C2F)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = containerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !quizState.isFinished) {
                                onSelectAnswer(quizState.currentIndex, optIndex)
                            }
                            .testTag("option_${quizState.currentIndex}_$optIndex")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val optionLetter = ('A' + optIndex).toString()
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected || (quizState.isFinished && isCorrect)) SlatyMint else Color(0xFF2C2740)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionLetter,
                                    color = if (isSelected || (quizState.isFinished && isCorrect)) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = optText,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            if (quizState.isFinished) {
                                if (isCorrect) {
                                    Icon(Icons.Default.Check, contentDescription = "Correct", tint = SlatyMint)
                                } else if (isSelected) {
                                    Icon(Icons.Default.Close, contentDescription = "Wrong", tint = SlatyError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
