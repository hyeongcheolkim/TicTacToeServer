package kim.hyeongcheol.tictactoeserver.dto;

import kim.hyeongcheol.tictactoeserver.game.GameRoom;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class RoomStateDto {
    private String roomId;
    private String roomName;
    private String hostNickname;
    private List<PlayerInfo> players;
    private Set<String> readyPlayerSessionIds;
    private TicTacToeGameDto game;
    private GameRoom.GameState gameState;
}