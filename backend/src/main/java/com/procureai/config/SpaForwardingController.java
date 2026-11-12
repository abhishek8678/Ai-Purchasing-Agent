package com.procureai.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * In the single-image deployment the built React app is served from {@code classpath:/static}. React Router uses
 * browser paths such as {@code /approvals}; a page refresh on such a path must return {@code index.html}.
 * API ({@code /api/**}) and actuator routes are unaffected because only these UI paths are forwarded.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/inventory", "/agent", "/agent/**", "/approvals", "/purchase-orders", "/activity", "/overview"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
