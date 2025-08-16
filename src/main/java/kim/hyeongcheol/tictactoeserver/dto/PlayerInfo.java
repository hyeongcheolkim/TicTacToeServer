package kim.hyeongcheol.tictactoeserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerInfo {
    private String sessionId;
    private String nickname;
    private PlayerRole role;
}