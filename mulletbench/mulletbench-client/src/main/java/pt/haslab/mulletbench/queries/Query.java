package pt.haslab.mulletbench.queries;

import pt.haslab.mulletbench.OperationType;

public record Query (String queryString, OperationType type) {
}
