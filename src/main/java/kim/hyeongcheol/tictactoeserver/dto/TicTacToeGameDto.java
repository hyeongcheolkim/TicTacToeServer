package kim.hyeongcheol.tictactoeserver.dto;

import kim.hyeongcheol.tictactoeserver.domain.TicTacToeGame;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicTacToeGameDto {
    private final Character[] board;
    private final String playerXSessionId;
    private final String playerOSessionId;
    private String currentPlayerSessionId;
    private String winnerSessionId;
    private boolean gameOver;

    public static TicTacToeGameDto of(TicTacToeGame game) {
        if (game == null) {
            return null;
        }
        return TicTacToeGameDto.builder()
                .board(game.getBoard())
                .playerXSessionId(game.getPlayerXSessionId())
                .playerOSessionId(game.getPlayerOSessionId())
                .currentPlayerSessionId(game.getCurrentPlayerSessionId())
                .winnerSessionId(game.getWinnerSessionId())
                .gameOver(game.isGameOver())
                .build();
    }
}