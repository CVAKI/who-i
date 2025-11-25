package com.humangodcvaki.whoi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_MY_MESSAGE = 1;
    private static final int VIEW_TYPE_OTHER_MESSAGE = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;
    private static final int VIEW_TYPE_GAME_INVITATION = 4;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUserId;
    private GameInvitationClickListener gameInvitationClickListener;

    public MessageAdapter() {
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
    }

    private String formatTime(long timestamp) {
        return timestamp == 0 ? "" :
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);

        // Check for system messages first
        if (message.isSystemMessage()) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        }

        // Check if it's a game invitation message
        if (message instanceof ChatActivity.GameInvitationMessage) {
            return VIEW_TYPE_GAME_INVITATION;
        }

        // Check if the message text contains game invitation indicator
        if (message.getText() != null && message.getText().contains("🎮") &&
                message.getText().contains("invited you to play")) {
            return VIEW_TYPE_GAME_INVITATION;
        }

        // Regular messages
        return message.getSenderId().equals(currentUserId)
                ? VIEW_TYPE_MY_MESSAGE
                : VIEW_TYPE_OTHER_MESSAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case VIEW_TYPE_MY_MESSAGE:
                return new MessageViewHolder(
                        inflater.inflate(R.layout.item_my_message, parent, false),
                        false
                );
            case VIEW_TYPE_SYSTEM_MESSAGE:
                return new SystemMessageViewHolder(
                        inflater.inflate(R.layout.item_system_message, parent, false)
                );
            case VIEW_TYPE_GAME_INVITATION:
                return new GameInvitationViewHolder(
                        inflater.inflate(R.layout.item_game_invitation, parent, false)
                );
            default: // VIEW_TYPE_OTHER_MESSAGE
                return new MessageViewHolder(
                        inflater.inflate(R.layout.item_other_message, parent, false),
                        true
                );
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        String time = formatTime(message.getTimestamp());

        if (holder instanceof GameInvitationViewHolder) {
            ChatActivity.GameInvitationMessage gameMessage;
            if (message instanceof ChatActivity.GameInvitationMessage) {
                gameMessage = (ChatActivity.GameInvitationMessage) message;
            } else {
                // Create a GameInvitationMessage from regular message for game invites
                gameMessage = createGameInvitationFromMessage(message);
            }
            ((GameInvitationViewHolder)holder).bind(
                    gameMessage,
                    time,
                    gameInvitationClickListener,
                    currentUserId
            );
        }
        else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder)holder).bind(message, time);
        }
        else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder)holder).bind(message, time);
        }
    }

    // Helper method to create GameInvitationMessage from regular message
    private ChatActivity.GameInvitationMessage createGameInvitationFromMessage(Message message) {
        // Extract gameRoomId from message if it's stored somewhere, or generate a new one
        String gameRoomId = "game_" + System.currentTimeMillis(); // Fallback
        return new ChatActivity.GameInvitationMessage(
                message.getSenderId(),
                message.getSenderName(),
                message.getText(),
                message.getTimestamp(),
                gameRoomId,
                "pending"
        );
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Add a single message
    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    // Replace all messages
    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    // Clear all messages
    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    public interface GameInvitationClickListener {
        void onGameInvitationClicked(String gameRoomId, String inviterName);
    }

    public void setGameInvitationClickListener(GameInvitationClickListener listener) {
        this.gameInvitationClickListener = listener;
    }

    static class GameInvitationViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView senderName;
        private final TextView timestamp;
        private final Button gameInviteButton;

        public GameInvitationViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            senderName = itemView.findViewById(R.id.senderName);
            timestamp = itemView.findViewById(R.id.timestamp);
            gameInviteButton = itemView.findViewById(R.id.gameInviteButton);
        }

        public void bind(ChatActivity.GameInvitationMessage message, String time,
                         GameInvitationClickListener listener, String currentUserId) {
            if (messageText != null) {
                messageText.setText(message.getText());
            }
            if (senderName != null) {
                senderName.setText(message.getSenderName());
            }
            if (timestamp != null) {
                timestamp.setText(time);
            }

            if (gameInviteButton != null) {
                boolean isReceiver = !message.getSenderId().equals(currentUserId);
                boolean isPending = "pending".equals(message.invitationStatus);

                if (isReceiver && isPending) {
                    gameInviteButton.setVisibility(View.VISIBLE);
                    gameInviteButton.setText("Accept Game");
                    gameInviteButton.setEnabled(true);
                    gameInviteButton.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onGameInvitationClicked(message.gameRoomId, message.getSenderName());
                        }
                        gameInviteButton.setEnabled(false);
                        gameInviteButton.setText("Processing...");
                    });
                }
                else if (message.getSenderId().equals(currentUserId)) {
                    gameInviteButton.setVisibility(View.VISIBLE);
                    gameInviteButton.setText("Invitation Sent");
                    gameInviteButton.setEnabled(false);
                    gameInviteButton.setOnClickListener(null);
                }
                else {
                    gameInviteButton.setVisibility(View.GONE);
                }
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textMessage;
        private final TextView textSenderName;
        private final TextView textTimestamp;
        private final boolean showSenderName;

        public MessageViewHolder(@NonNull View itemView, boolean showSenderName) {
            super(itemView);
            this.showSenderName = showSenderName;

            // Find views with multiple possible IDs to handle layout variations
            textMessage = findTextView(itemView, R.id.textMessage, R.id.messageText);
            textSenderName = findTextView(itemView, R.id.textSenderName, R.id.senderName);
            textTimestamp = findTextView(itemView, R.id.textTimestamp, R.id.timestamp);
        }

        // Helper method to find TextView with multiple possible IDs
        private TextView findTextView(View itemView, int... ids) {
            for (int id : ids) {
                TextView textView = itemView.findViewById(id);
                if (textView != null) {
                    return textView;
                }
            }
            return null;
        }

        // Bind regular messages
        public void bind(Message message, String formattedTime) {
            if (textMessage != null) {
                textMessage.setText(message.getText());
            }

            if (textSenderName != null) {
                if (showSenderName && !message.isSystemMessage()) {
                    textSenderName.setVisibility(View.VISIBLE);
                    textSenderName.setText(message.getSenderName());
                } else {
                    textSenderName.setVisibility(View.GONE);
                }
            }

            if (textTimestamp != null) {
                textTimestamp.setText(formattedTime);
            }
        }
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textMessage;
        private final TextView textTimestamp;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
        }

        public void bind(Message message, String formattedTime) {
            if (textMessage != null) {
                textMessage.setText(message.getText());
            }
            if (textTimestamp != null) {
                textTimestamp.setText(formattedTime);
            }
        }
    }
}