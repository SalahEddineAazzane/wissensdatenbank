package de.salaheddineaazzane.wissensdatenbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notizen")
public class NotizController {

    public record NotizAnfrage(@NotBlank String titel, String inhalt, List<String> tags) {}

    private final NotizService service;

    public NotizController(NotizService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotizService.Notiz erstelle(@Valid @RequestBody NotizAnfrage a) {
        long id = service.erstelle(a.titel(), a.inhalt(), a.tags());
        return service.finde(id).orElseThrow();
    }

    @GetMapping("/{id}")
    public NotizService.Notiz holen(@PathVariable long id) {
        return service.finde(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public NotizService.Notiz aendern(@PathVariable long id, @Valid @RequestBody NotizAnfrage a) {
        if (!service.aendere(id, a.titel(), a.inhalt(), a.tags())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return service.finde(id).orElseThrow();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(@PathVariable long id) {
        if (!service.loesche(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/suche")
    public List<NotizService.Treffer> suche(@RequestParam String q,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(defaultValue = "0") int seite,
                                            @RequestParam(defaultValue = "10") int groesse) {
        return service.suche(q, tag, seite, groesse);
    }

    @GetMapping("/{id}/historie")
    public List<NotizService.Historie> historie(@PathVariable long id) {
        return service.historie(id);
    }
}