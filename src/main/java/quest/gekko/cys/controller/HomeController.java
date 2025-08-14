package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import quest.gekko.cys.domain.Platform;

@Controller
@RequiredArgsConstructor
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        // Add default platform for the search form
        model.addAttribute("platform", Platform.YOUTUBE);
        return "index";
    }
}