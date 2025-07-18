package io.qimo.usdtzero.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @PostMapping("/api/test")
    public String test(@RequestBody String body) {
        System.out.println("收到body: [" + body + "]");
        return "ok";
    }
}
