package dev.localassistant.assistant.weather.domain.port.inbound;

import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.weather.domain.Location;
import dev.localassistant.assistant.weather.domain.WeatherReport;

import java.util.Objects;

public interface ResolveWeatherObservation {

    ToolExecutionResult<WeatherReport> execute(Command command);

    record Command(Location location) {
        public Command {
            Objects.requireNonNull(location, "location");
        }
    }
}
