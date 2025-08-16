package kim.hyeongcheol.tictactoeserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class GameMessage {

    public enum MessageType {
        JOIN,
        LEAVE,
        CHAT,
        READY,
        UNREADY,
        MOVE,
        KICK,
        ERROR,
        GAME_START,
        GAME_UPDATE,
        GAME_END,
    }

    private MessageType type;
    private String roomId;
    private String sender;
    private String content;
    private PlayerRole senderRole;
    private MovePayload move;
    private RoomStateDto roomState;
    private String kickTargetSessionId;
}