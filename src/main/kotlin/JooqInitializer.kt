package com.perpheads.files

import io.quarkiverse.jooq.runtime.JooqCustomContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Named
import org.jooq.Configuration
import org.jooq.conf.RenderNameCase

@ApplicationScoped
class JooqInitializer {
    @Named("jooqConfigurator")
    fun create(): JooqCustomContext {
        return object : JooqCustomContext {
            override fun apply(configuration: Configuration) {
                super.apply(configuration)
                configuration.settings().renderNameCase = RenderNameCase.LOWER
                configuration.settings().isRenderGroupConcatMaxLenSessionVariable = false
            }
        }
    }
}