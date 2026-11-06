package com.vyapaaros.ai.provider;

import com.vyapaaros.ai.dto.AiProviderItem;
import java.util.List;

public interface AiOrderProvider {
    List<AiProviderItem> extract(String text);
}
