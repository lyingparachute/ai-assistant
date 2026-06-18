package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.ProductPageResult;
import org.apache.commons.lang3.StringUtils;

public interface ProductPageSource {

    ProductPageResult fetchAndExtract(Command command);

    record Command(String sourceUrl) {

        public Command {
            if (StringUtils.isBlank(sourceUrl)) {
                throw new IllegalArgumentException("sourceUrl must not be blank");
            }
        }
    }
}
