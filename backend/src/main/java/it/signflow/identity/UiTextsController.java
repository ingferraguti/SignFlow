package it.signflow.identity;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui-texts")
public class UiTextsController {
    private final AdminUiTextsRepository repository;

    public UiTextsController(AdminUiTextsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    Map<String, String> findAll() {
        return repository.findAll();
    }
}
