package com.yean.common.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.function.Function;

/**
 * Swagger / 浏览器不填可选数字参数时会传空串，Spring 默认无法转成 Long/Integer。
 */
@ControllerAdvice
public class EmptyStringToNullAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new BlankAsNullEditor(Long::valueOf));
        binder.registerCustomEditor(Integer.class, new BlankAsNullEditor(Integer::valueOf));
    }

    private static final class BlankAsNullEditor extends PropertyEditorSupport {

        private final Function<String, Object> parser;

        private BlankAsNullEditor(Function<String, Object> parser) {
            this.parser = parser;
        }

        @Override
        public void setAsText(String text) {
            if (text == null || text.isBlank()) {
                setValue(null);
                return;
            }
            setValue(parser.apply(text.trim()));
        }
    }
}
