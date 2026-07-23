package it.signflow.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/ui-texts")
public class AdminUiTextsController {
    private final AdminUiTextsRepository repository;

    public AdminUiTextsController(AdminUiTextsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    Map<String, String> findAll() {
        return repository.findAll();
    }

    @PutMapping
    Map<String, String> update(@Valid @RequestBody Map<@NotBlank @Size(max = 100) String,
            @NotBlank @Size(max = 300) String> values) {
        Map<String, String> known = repository.findAll();
        if (!known.keySet().containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unknown UI text key");
        }
        return repository.update(values);
    }
}
