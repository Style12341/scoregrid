package com.scoregrid.score.score.domain.port.out;

import java.util.List;
import java.util.Map;

public interface AuthClientPort {

    Map<String, String> getUsernames(List<String> userIds);
}
