/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2021 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.action.ex

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.extension.VimExtensionFacade
import com.maddyhome.idea.vim.extension.VimExtensionFacade.inputString
import com.maddyhome.idea.vim.handler.VimActionHandler
import com.maddyhome.idea.vim.helper.StringHelper
import com.maddyhome.idea.vim.option.ClipboardOptionsData
import com.maddyhome.idea.vim.vimscript.model.VimContext
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimDataType
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimDictionary
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimList
import com.maddyhome.idea.vim.vimscript.parser.VimscriptParser

class ExpressionEvaluationAction : VimActionHandler.SingleExecution() {

  override val type: Command.Type = Command.Type.INSERT

  private val logger = logger<ExpressionEvaluationAction>()
  override fun execute(editor: Editor, context: DataContext, cmd: Command): Boolean {
    var res = true
    try {
      logger.debug("processing expression evaluation")
      var input = readExpression(editor)
      if (input.isBlank()) {
        val expressionRegisterValue = VimPlugin.getRegister().getRegister('=')?.text
        input = if (expressionRegisterValue == null || expressionRegisterValue.isBlank()) {
          "''"
        } else {
          expressionRegisterValue
        }
      }

      val expression = VimscriptParser.parseExpression(input) ?: throw ExException("E15: Invalid expression: $input")
      VimPlugin.getRegister().storeTextSpecial('=', input)

      val expressionValue: VimDataType = expression.evaluate(editor, context, VimContext())
      perform(expressionValue.getStringToInsert(), editor)
    } catch (e: ExException) {
      VimPlugin.showMessage(e.message)
      VimPlugin.indicateError()
      res = false
    } catch (bad: Exception) {
      logger.error(bad)
      VimPlugin.indicateError()
      res = false
    }
    return res
  }

  private fun readExpression(editor: Editor): String {
    return inputString(editor, "=", null)
  }

  private fun VimDataType.getStringToInsert(): String {
    return when (this) {
      is VimList -> this.values.joinToString { it.toString() + "\n" }
      is VimDictionary -> throw ExException("E731: using Dictionary as a String")
      else -> this.toString()
    }
  }

  private fun perform(sequence: String, editor: Editor) {
    ClipboardOptionsData.IdeaputDisabler().use {
      VimExtensionFacade.executeNormalWithoutMapping(StringHelper.parseKeys(sequence), editor)
    }
  }
}
