package kim.hyeongcheol.tictactoeserver.advice;

import kim.hyeongcheol.tictactoeserver.dto.GameMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class StompExceptionAdvice {

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public GameMessage handleException(Exception ex) {

        log.error("STOMP Error: {}", ex.getMessage(), ex);

        return GameMessage.builder()
                .type(GameMessage.MessageType.ERROR)
                .content("요청 처리 중 서버에서 예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                .build();
    }
}