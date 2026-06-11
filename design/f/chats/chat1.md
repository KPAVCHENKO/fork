# Chat Design Fork

_Started 2026-06-10 22:57 UTC_

---

## User

<system-info comment="Only acknowledge these if relevant">
Project title is now "f"
Project is currently empty (no files)
Current date is now June 11, 2026
</system-info>

<pasted_text name="Pasted text (69 lines)">
# Задача: дизайн-система для Fork — современного Android-мессенджера (форк Telegram)

Ты — senior product-дизайнер мобильных приложений. Сделай полный визуальный
редизайн Android-мессенджера **Fork** в виде дизайн-спецификации, которую
разработчик сможет реализовать в Jetpack Compose без догадок.

## О приложении
- Fork — кастомный клиент Telegram (на TDLib) для семьи и друзей. Уже умеет:
  чаты, фото/видео/GIF, голосовые с waveform, стикеры, ответы (reply), поиск,
  уведомления, настройки, самообновление.
- Аудитория приходит из Telegram: **UX-паттерны менять нельзя** (всё на своих
  привычных местах), а визуально должно ощущаться свежее, стильнее и «дороже»
  оригинала.
- Бренд: имя **Fork**, фирменные цвета — индиго `#2362FD` и циан `#00B8D9`
  (как на иконке). Палитру можно развивать, но эта пара — узнаваемая основа.

## Направление стиля
Стильно, молодёжно, по-современному: глубокие тёмные поверхности, сочные
градиенты бренда в акцентах, крупная уверенная типографика, мягкие большие
радиусы, воздух между элементами. Тёмная тема — главная (плюс опция чисто
чёрной AMOLED), светлая — равноправная. Никакого визуального шума.

## Технические рамки (обязательно соблюдать)
- Только то, что реализуемо штатным **Jetpack Compose + Material 3**:
  цвета, формы, градиенты, blur, spring/tween-анимации. Без Lottie, без 3D,
  без платных шрифтов (системный Roboto или вариативный Google-шрифт — ок).
- Все цвета описать как роли **Material 3 ColorScheme** (primary, onPrimary,
  primaryContainer, secondary, surface, surfaceVariant, outline, error и т.д.)
  таблицей с HEX — отдельно для светлой и тёмной темы.
- Чего нет в Material 3 — вынести в отдельный набор **Fork tokens**:
  пузыри сообщений (входящие/исходящие), waveform голосовых, галочки статуса,
  счётчик непрочитанных, индикатор online, 7 градиентных пар для аватаров.
- Размеры в **dp**, текст в **sp**, радиусы и отступы — конкретные числа.
- Анимации: словами + длительность и easing (напр. «tween 200ms FastOutSlowIn»
  или «spring, dampingRatio 0.8»).
- Должна сохраниться опция Material You (динамические цвета Android 12+).

## Экраны для редизайна (все уже существуют)
1. **Логин** (телефон → код → пароль) — первое впечатление, должен «продавать»
   приложение: бренд, градиент, характер.
2. **Список чатов**: аватар, имя, превью сообщения, время, счётчик
   непрочитанных, галочки статуса, строка поиска, баннер «доступно обновление».
3. **Экран чата** (самый важный): пузыри входящих/исходящих, текст, фото,
   видео, GIF, голосовые с waveform, документы, стикеры, цитаты-ответы,
   дата-разделители, поле ввода (скрепка / микрофон / отправить), запись
   голосового удержанием, кнопка «вниз», контекстное меню сообщения.
4. **Поиск** — локальные чаты + глобальный по @имени.
5. **Настройки** — тема, цвета, уведомления, поведение, обновления.
6. **Просмотр медиа** — фото с зумом, видеоплеер, плеер голосовых.

## Что сдать: один markdown-документ «Fork Design Spec»
1. **Видение** — короткий манифест: характер, настроение, 3–5 принципов.
2. **Токены** — палитры light/dark (таблицы M3-ролей с HEX), Fork tokens,
   типографическая шкала (имя стиля / sp / вес / межстрочный), радиусы,
   шкала отступов (4/8/12/16…), elevation.
3. **Спека каждого экрана** — структура, размеры, состояния
   (пусто / загрузка / ошибка), чем экран выделяется визуально.
4. **Библиотека компонентов** — аватар, пузырь, ячейка чата, поле ввода,
   кнопки, бейджи, галочки, разделители.
5. **Моушн** — переходы экранов, появление сообщений, микроанимации.
6. **7 фирменных деталей** — мелочи, делающие Fork узнаваемым, но дешёвые
   в реализации.

Если умеешь показывать визуальные макеты — сначала покажи 3 ключевых экрана
(логин, список чатов, чат) в тёмной теме, дождись моего «ок» по стилю,
и только потом пиши полную спецификацию.

Не нужно: новых фич и экранов, изменения UX-логики, кода — только спецификация.

</pasted_text>

<!-- The user explicitly selected the following skills for this project, as attachments to their message. These are not optional context — they define how you work. Use them. -->
<attached-skill name="Hi-fi design">
Create a high-fidelity, polished design.

Follow this general design process (use the todo list to remember):
(1) ask questions, (2) find existing UI kits and collect design context — copy ALL relevant components and read ALL relevant examples; ask the user if you can't find them, (3) start your file with assumptions + context + design reasoning (as if you are a junior designer and the user is your manager), with placeholders for the designs, and show it to the user early, (4) build out the designs and show the user again ASAP; append some next steps, (5) use your tools to check, verify and iterate on the design.

Good hi-fi designs do not start from scratch — they are rooted in existing design context. Ask the user to Import their codebase, or find a suitable UI kit / design resources, or ask for screenshots of existing UI. You MUST spend time trying to acquire design context, including components. If you cannot find them, ask the user for them. In the Import menu, they can link a local codebase, provide screenshots or Figma links; they can also link another project. Mocking a full product from scratch is a LAST RESORT and will lead to poor design. If stuck, try listing design assets and ls'ing design system files — be proactive! Some designs may need multiple design systems — get them all. Use the starter components (device frames and the like) to get high-quality scaffolding for free.

When presenting several options or explorations side-by-side, lay them out clearly: give the page a neutral gray background, put each option in its own labeled frame (a small heading + a white rounded card sized to its content), and group related options into sections.

When designing, asking many good questions is ESSENTIAL.

Give options: try to give 3+ variations across several dimensions. Mix by-the-book designs that match existing patterns with new and novel interactions, including interesting layouts, metaphors, and visual styles. Have some options that use color or advanced CSS; some with iconography and some without. Start your variations basic and get more advanced and creative as you go! Try remixing the brand assets and visual DNA in interesting ways — play with scale, fills, texture, visual rhythm, layering, novel layouts, type treatments. The goal is not the perfect option; it's exploring atomic variations the user can mix and match.

CSS, HTML, JS and SVG are amazing. Users often don't know what they can do. Surprise the user.

If you do not have an icon, asset or component, draw a placeholder: in hi-fi design, a placeholder is better than a bad attempt at the real thing.
</attached-skill>

<attached-skill name="Design Components">
This project uses Design Components: every design is a single streaming `Name.dc.html` file. The full authoring spec is in your system prompt under "Writing code — Design Components" — follow it. Author and edit `.dc.html` content ONLY with the `dc_write`, `dc_html_str_replace`, and `dc_js_str_replace` tools (never `write_file` or `str_replace_edit`); template edits stream into the live preview as you type.
</attached-skill>

<attached-skill name="Design System (design system)">
[Design System] This project uses the **Design System** design system. This is a binding choice for visual style — every visual must follow it. Don't invent colors, type, spacing, or components not grounded here.

Scope: the design system is a visual style reference only. Its guide may describe example products, brands, or people that are unrelated to the user and unrelated to the subject of this conversation. Never treat anything in the design system as a fact about the user, their work, or the topic they asked about.

    Explore it to find what you need:
    - Always copy out the fonts and colors you need
    - For prototypes and designs, always copy out any relevant components
    - If the design system contains existing mocks of products, and you were asked to design or prototype something similar, copy and fork those mocks to start your design. This helps you make high-quality designs.

    Explore it quickly to find relevant UI kits (e.g. mocks of existing products you can copy and fork)

Full system at `/projects/9fb6b15e-9129-4c1d-a274-e444ff217248/`. Before producing any visuals, explore it: call `list_files("/projects/9fb6b15e-9129-4c1d-a274-e444ff217248/")` to see the structure, then `read_file` the README/base.md or whatever index file it has. Don't guess at the design system's contents.

CSS tokens: the guide may describe tokens in prose, but the exact `--*` names are defined in the design system's stylesheet(s). Before writing any `var(--*)`, look up the real name in the design system's `.css` files (`list_files` + `read_file` under `/projects/9fb6b15e-9129-4c1d-a274-e444ff217248/`). Never guess a token name — an unresolved `var()` silently falls back to the browser default.

For assets and UI kits beyond the guide: `read_file("/projects/9fb6b15e-9129-4c1d-a274-e444ff217248/<path>")` and `copy_files` to bring them into the current project.
</attached-skill>

## Assistant

_[tool: list_files]_

_[tool: questions_v2]_

## User

Questions answered:
- variants: 3 направления
- gradient_dose: Смело: градиентные шапки, крупные брендовые заливки
- outgoing_bubble: Градиент индиго→циан (фирменная фишка)
- font: Manrope (геометричный, молодёжный)
- corner_mood: Очень круглая: пузыри 20dp+, карточки 24–28dp
- dark_base: Глубокий сине-серый (холодный, в тон индиго)
- density: Decide for me
- lang: Русский (имена, сообщения — на русском)
- spec_lang: Decide for me
- signature: Фирменные детали Fork (все должны быть дёшевы в реализации на Jetpack Compose):

1. **Живой градиент бренда** — единый диагональный градиент индиго #2362FD → циан
   #00B8D9 на всех ключевых акцентах: кнопка отправки, счётчик непрочитанных,
   заголовок на логине. На экране логина фон с этим градиентом медленно «дышит».
2. **Градиентные пузыри исходящих** — свои сообщения залиты этим же градиентом
   (как в Telegram, но в нашей палитре); входящие — спокойная поверхность.
3. **Онлайн-кольцо** — когда собеседник в сети, его аватар обведён тонким
   градиентным кольцом вместо привычной зелёной точки.
4. **Микрофон ⇄ отправка** — кнопка в поле ввода пружинно морфит из микрофона
   в градиентную кнопку отправки, как только начинаешь печатать.
5. **Waveform в градиенте** — дорожка голосового прокрашивается фирменным
   градиентом по мере проигрывания.
6. **Стеклянные капсулы** — дата-разделители в чате и кнопка «вниз» —
   полупрозрачные «стеклянные» пилюли с лёгким blur.
7. **Циановые галочки** — галочки «прочитано» цвета циан — мгновенно узнаваемая
   мелочь статуса.
8. **Пружинное появление** — новое сообщение влетает с лёгким spring-подскоком
   и fade; свайп-для-ответа тянется с физикой пружины.
9. **Вилка в пустых состояниях** — простая линейная иллюстрация-вилка (логотип)
   для «нет сообщений» / «ничего не найдено», одним векторным контуром.

Используй эти детали последовательно во всех экранах — они и есть характер Fork.

## Assistant

## Assistant

_[tool: copy_starter_component]_

_[tool: update_todos]_

Отлично — три направления, смелые градиенты, Manrope, очень круглая геометрия, глубокий сине-серый. Делаю макеты трёх ключевых экранов в тёмной теме для каждого направления.

_[tool: read_file]_

_[tool: dc_write]_

_[tool: dc_html_str_replace]_

Направление 1 готово. Добавляю направление 2 — «Frost» (стекло и амбиентные свечения).

_[tool: dc_html_str_replace]_

Теперь направление 3 — «Neon Ink».

_[tool: set_project_title]_

_[tool: done]_

_[tool: fork_verifier_agent]_

Готово — три направления стиля Fork, по три экрана в тёмной теме (логин → список чатов → чат):

- **01 Aurora** — градиентный кэп на списке чатов, спокойные сине-серые поверхности; самый «безопасный»
- **02 Frost** — амбиентные свечения фона + стеклянные панели с blur (шапка, поиск, ввод, входящие пузыри)
- **03 Neon Ink** — почти чёрная база, крупная типографика, градиент как неон (кольца, контур ввода, свечения)

Все ваши фирменные детали уже в макетах: градиентные исходящие, онлайн-кольцо, циановые галочки, waveform в градиенте, стеклянные капсулы, баннер обновления.

Дальше: скажите «ок» по одному направлению (или микс — например, «база 02, пузыри и бейджи 03») — и я соберу полный **Fork Design Spec** в markdown: M3-палитры light/dark, Fork-токены, типографика, спеки всех 6 экранов, компоненты, моушн и 7 фирменных деталей.

## User

Давай  Сделай возможность чтобы сразу 3 эти темы можно было добавить и переключаться между ними в настройках потом

## Assistant

_[tool: update_todos]_

Отлично — тогда все три направления становятся **тремя встроенными темами Fork** (Aurora / Frost / Neon Ink) с переключателем в настройках. Пишу полную спецификацию с учётом этой архитектуры.

_[tool: write_file]_

## Assistant

_[tool: write_file]_

_[tool: show_to_user]_

Готово — **Fork Design Spec.md** написан с учётом того, что все три стиля живут в приложении одновременно:

- **§2 Архитектура тем** — тема = Стиль (Aurora / Frost / Neon Ink) × Режим (light / dark / system) + AMOLED + Material You; хранение в DataStore, кроссфейд 300ms при переключении
- **§3 Токены** — общая светлая палитра M3 + три тёмные палитры по стилям + AMOLED-надстройка; Fork-токены (пузыри, waveform, галочки, стекло, glow, 7 пар аватаров); Manrope-шкала, радиусы, отступы, elevation
- **§4.5 Настройки** — спека переключателя: три превью-карточки стилей, сегмент-контрол режима, AMOLED- и Material You-свитчи
- Плюс спеки всех 6 экранов с состояниями, библиотека компонентов, моушн-таблица и 9 фирменных деталей

Каверзное место отметил в чек-листе: на устройствах < Android 12 у Frost нет настоящего blur — предусмотрен откат к плотному tint без размытия.

Следующие шаги, если нужно: макет самого экрана настроек с переключателем тем, светлые варианты трёх стилей, или экспорт спеки в PDF.

