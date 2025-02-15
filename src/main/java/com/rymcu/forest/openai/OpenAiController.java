package com.rymcu.forest.openai;

import com.alibaba.fastjson.JSONObject;
import com.rymcu.forest.core.result.GlobalResult;
import com.rymcu.forest.core.result.GlobalResultGenerator;
import com.rymcu.forest.entity.User;
import com.rymcu.forest.openai.service.OpenAiService;
import com.rymcu.forest.openai.service.SseService;
import com.rymcu.forest.util.UserUtils;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import io.reactivex.Flowable;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Created on 2023/2/15 10:04.
 *
 * @author ronger
 * @email ronger-x@outlook.com
 * @desc : com.rymcu.forest.openai
 */
@RestController
@RequestMapping("/api/v1/openai")
public class OpenAiController {
    @Resource
    private SseService sseService;

    @Value("${openai.token}")
    private String token;

    @PostMapping("/chat")
    public GlobalResult chat(@RequestBody JSONObject jsonObject) {
        String message = jsonObject.getString("message");
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("参数异常！");
        }
        User user = UserUtils.getCurrentUserByToken();
        ChatMessage chatMessage = new ChatMessage("user", message);
        List<ChatMessage> list = new ArrayList<>(4);
        list.add(chatMessage);
        OpenAiService service = new OpenAiService(token, Duration.ofSeconds(180));
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .stream(true)
                .messages(list)
                .build();
        service.streamChatCompletion(completionRequest).doOnError(Throwable::printStackTrace)
                .blockingForEach(chunk -> {
                    String text = chunk.getChoices().get(0).getMessage().getContent();
                    if (text == null) {
                        return;
                    }
                    System.out.print(text);
                    sseService.send(user.getIdUser(), text);
                });
        service.shutdownExecutor();
        return GlobalResultGenerator.genSuccessResult();
    }
}
