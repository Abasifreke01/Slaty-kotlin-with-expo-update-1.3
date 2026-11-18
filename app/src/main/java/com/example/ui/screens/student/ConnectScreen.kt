package com.example.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.model.Community
import com.example.model.PrivateConversation
import com.example.ui.theme.*

@Composable
fun ConnectScreen(
    communities: List<Community>,
    joinedCommunityIds: Set<String> = emptySet(),
    currentUserId: String = "",
    privateChats: List<PrivateConversation>,
    onCommunityClick: (Community) -> Unit,
    onJoinCommunity: (String) -> Unit = {},
    onLeaveCommunity: (String) -> Unit = {},
    onDeleteCommunity: (Community) -> Unit = {},
    onPrivateChatClick: (PrivateConversation) -> Unit,
    onCreateCommunityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSegment by remember { mutableStateOf("communities") } // "communities" or "chats"
    var communityPendingDelete by remember { mutableStateOf<Community?>(null) }

    communityPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { communityPendingDelete = null },
            containerColor = Color(0xFF1F1C2F),
            title = {
                Text(
                    text = "Delete Study Group?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Permanently delete '${target.name}' and its messages across all devices? This cannot be undone.",
                    color = SlatyTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCommunity(target)
                        communityPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatyError),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_delete_community_button")
                ) {
                    Text("Delete Group", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { communityPendingDelete = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlatyDarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F1C2F))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Communities tab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedSegment = "communities" }
                    .testTag("segment_communities"),
                color = if (selectedSegment == "communities") SlatyMint else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Communities (${communities.size})",
                        color = if (selectedSegment == "communities") Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Private Chats tab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedSegment = "chats" }
                    .testTag("segment_chats"),
                color = if (selectedSegment == "chats") SlatyMint else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Direct Chats (${privateChats.size})",
                        color = if (selectedSegment == "chats") Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Sub-header with Create action for communities
        if (selectedSegment == "communities") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Study Groups & Hubs",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Join a group to receive notifications and participate in discussions",
                        color = SlatyTextSecondary,
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = onCreateCommunityClick,
                    modifier = Modifier.testTag("create_community_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = SlatyMint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Group", color = SlatyMint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Content List
        if (selectedSegment == "communities") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 130.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(communities, key = { it.id }) { comm ->
                    val isJoined = joinedCommunityIds.contains(comm.id)
                    val isCreator = comm.createdById.isNotBlank() &&
                        currentUserId.isNotBlank() &&
                        comm.createdById == currentUserId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (isJoined) {
                                    onCommunityClick(comm)
                                } else {
                                    onJoinCommunity(comm.id)
                                    onCommunityClick(comm)
                                }
                            }
                            .testTag("community_item_${comm.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F1C2F),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isJoined) SlatyMint.copy(alpha = 0.5f) else Color(0xFF332D48)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isJoined) SlatyMint.copy(alpha = 0.2f) else Color(0xFF2E2442)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = if (isJoined) SlatyMint else SlatyLavender,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = comm.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isJoined) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = SlatyMint.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "MEMBER",
                                                    color = SlatyMint,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (isCreator) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = SlatyPrimary.copy(alpha = 0.3f)
                                            ) {
                                                Text(
                                                    text = "CREATOR",
                                                    color = SlatyLavender,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (comm.unreadCount > 0 && isJoined) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(SlatyMint)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = comm.description,
                                        color = SlatyTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val creatorSuffix = if (comm.createdByName.isNotBlank()) " • by ${comm.createdByName}" else ""
                                    Text(
                                        text = "${comm.memberCount} active learners • ${comm.category}$creatorSuffix",
                                        color = Color(0xFF748C94),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCreator) {
                                    OutlinedButton(
                                        onClick = { communityPendingDelete = comm },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlatyError),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SlatyError.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 40.dp)
                                            .testTag("delete_community_${comm.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Study Group",
                                            tint = Color(0xFFFF8A80),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 11.sp, color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                if (isJoined) {
                                    OutlinedButton(
                                        onClick = { onLeaveCommunity(comm.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF552233)),
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("leave_comm_${comm.id}")
                                    ) {
                                        Text("Leave", fontSize = 11.sp, color = Color(0xFFFF8888))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { onCommunityClick(comm) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary),
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("enter_chat_${comm.id}")
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            onJoinCommunity(comm.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("join_community_btn_${comm.id}")
                                    ) {
                                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Join Group", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Private chats list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 130.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(privateChats, key = { it.id }) { chat ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPrivateChatClick(chat) }
                            .testTag("chat_item_${chat.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F1C2F),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D48))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B263F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.otherUserName.firstOrNull()?.uppercase() ?: "T",
                                    color = SlatyLavender,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = chat.otherUserName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = chat.lastMessageTime,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chat.lastMessage,
                                    color = SlatyTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }

                            if (chat.unreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(SlatyMint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${chat.unreadCount}",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
