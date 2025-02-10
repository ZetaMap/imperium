/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.mindustry.setting

import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor.WHITE
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.mindustry.component.status
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.user.UserSettingService

@Suppress("FunctionName")
internal fun SettingMenu(plugin: MindustryPlugin, settings: UserSettingService): MenuManager {
    val menu = MenuManager.create(plugin)
    menu.addTransformer { ctx ->
        ctx.pane.title = translatable("imperium.gui.user-settings.title")
        ctx.pane.content = translatable("imperium.gui.user-settings.description")
        for ((setting, value) in settings.selectSettings(ctx.viewer.uuid())) {
            val content =
                components()
                    .append(text(setting.name.lowercase().replace('_', '-'), WHITE))
                    .append(text(": "))
                    .append(status(value))
                    .build()
            val action =
                CoroutineAction(success = BiAction.from(Window::show)) { window ->
                    settings.toggleSetting(window.viewer.uuid(), setting)
                }
            ctx.pane.grid.addRow(MenuOption.of(content, action))
            ctx.pane.grid.addRow(
                MenuOption.of(
                    translatable("imperium.user-setting.${setting.name.lowercase()}.description"),
                    Action.none(),
                )
            )
        }
    }
    return menu
}
