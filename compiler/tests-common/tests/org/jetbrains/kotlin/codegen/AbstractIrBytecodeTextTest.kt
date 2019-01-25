/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.test.ConfigurationKind

abstract class AbstractIrBytecodeTextTest : AbstractBytecodeTextTest() {
    override fun updateConfiguration(configuration: CompilerConfiguration) = configuration.put(JVMConfigurationKeys.IR, true)

    override fun extractConfigurationKind(files: MutableList<TestFile>): ConfigurationKind = ConfigurationKind.ALL
}