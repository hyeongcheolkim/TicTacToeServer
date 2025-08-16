package kim.hyeongcheol.tictactoeserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomInfo {
    private String roomId;
    private String roomName;
    private long playerCount;
    private String hostNickname;
}