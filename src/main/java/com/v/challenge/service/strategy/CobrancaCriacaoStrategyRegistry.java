package com.v.challenge.service.strategy;

import com.v.challenge.domain.CobrancaMetodoEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CobrancaCriacaoStrategyRegistry {

    private final Map<CobrancaMetodoEnum, CobrancaCriacaoStrategy> strategies;

    public CobrancaCriacaoStrategyRegistry(List<CobrancaCriacaoStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(
                CobrancaCriacaoStrategy::getMetodo,
                Function.identity()
            ));
        log.info("Strategies registradas: {}", strategies.keySet());
    }

    public CobrancaCriacaoStrategy getStrategy(CobrancaMetodoEnum metodo) {
        CobrancaCriacaoStrategy strategy = strategies.get(metodo);
        if (strategy == null) {
            throw new IllegalArgumentException(
                "Strategy não encontrada para método: " + metodo
            );
        }
        return strategy;
    }
}
