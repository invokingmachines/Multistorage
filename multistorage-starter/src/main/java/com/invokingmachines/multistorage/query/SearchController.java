package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.query.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/multistorage/api/{target}/search")
@Tag(name = "Search", description = "Execute search query. Readable columns in select, searchable in where.")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    @Operation(summary = "Execute search query", description = "Path: target (table alias). Body: Query (select, where). Only readable columns in select, only searchable in where.")
    public List<Map<String, Object>> search(@PathVariable String target, @RequestBody Query query) {
        return searchService.search(target, query);
    }
}
