# DriveNext — лабораторные работы №1 и №2

Android-приложение на Kotlin, минимальная версия Android 12 (API 31). Задание взято из `Разработка_мобильных_приложений.pdf`, страницы 50–74.

## Реализовано

- Лаба 1: SplashScreen и экран загрузки, проверка сети, экран без подключения, онбординг на ViewPager2 и сохранение его прохождения.
- Лаба 2: выбор «Войти» или «Зарегистрироваться»; вход по email/паролю и через Google OAuth; три шага регистрации с проверкой полей, выбором дат, отображением/скрытием пароля; выбор фото из галереи или с камеры; экран успешной регистрации; пустой главный экран и выход из аккаунта.
- Сетевые запросы выполняются к Supabase Auth, Storage и таблице `public.profiles`. Ошибки сервера показываются в диалоге, во время запросов отображается индикатор загрузки.
- Сессия сохраняется в SharedPreferences. После истечения access token используется refresh token. Для учебного приложения этого достаточно; в продуктовой версии токены следует перенести в защищённое хранилище.

## Настройка Supabase

Серверная часть уже настроена: таблица `profiles` доступна, закрытый bucket `identity-documents` и три политики видны в панели Storage. Google-провайдер включён, подтверждение email выключено. Если проект Supabase будет создан заново, выполните шаги ниже.

1. В панели Supabase откройте **SQL Editor**, выполните содержимое [`supabase/setup.sql`](supabase/setup.sql). Скрипт создаёт таблицу `profiles`, закрытый bucket `identity-documents` и политики доступа: пользователь видит только свои данные.
2. В **Authentication → URL Configuration** добавьте Redirect URL: `drivenext://auth-callback`.
3. Для Google-входа включите провайдер Google в **Authentication → Providers → Google**. У Google-провайдера должен быть настроен OAuth client ID и callback URL Supabase.
4. Для полного прохождения регистрации сразу после создания аккаунта выключите **Confirm email** в **Authentication → Sign In / Providers → User Signups**. Если подтверждение включено, приложение предложит подтвердить адрес и войти; фото и профиль до входа не загрузятся.
5. В `local.properties` должны быть строки `supabase.url=<Project URL>` и `supabase.publishableKey=<Publishable key>`. На этом компьютере они уже добавлены. Файл исключён из Git. Использовать `secret` или `service_role` в Android-приложении нельзя.

Проверить серверную настройку после этих действий: `python3 scripts/check_supabase.py`. Скрипт проверяет таблицу `profiles`, состояние Google-провайдера и подтверждение email. Закрытый bucket `identity-documents` проверьте в разделе Storage панели Supabase; без входа в аккаунт API может скрывать его наличие. Скрипт не создаёт пользователей и не изменяет данные.

## Запуск

1. Откройте папку проекта (`Разработка МП`) в Android Studio и дождитесь Gradle Sync.
2. Запустите подготовленный эмулятор `DriveNext_API_34` командой `sh scripts/run_light_emulator.sh` из корня проекта и дождитесь сообщения «Эмулятор запущен». Скрипт сам перезапустит этот AVD, если Android Studio уже открыла его без нужного DNS, и включит защищённый DNS.
3. Нажмите **Run app**.

Из терминала:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ru.student.drivenext/.presentation.MainActivity
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Нужны JDK 17 и Android SDK 35. Чтобы снова увидеть онбординг, очистите данные приложения или выполните `adb shell pm clear ru.student.drivenext`.

## Если эмулятор тормозит

На этом Mac подготовлен отдельный облегчённый AVD **DriveNext_API_34**: Android 14 с Google Play, Pixel 3a, около 2,5 ГБ RAM, 4 ядра, экран 720×1480. **Сначала** запустите его из корня проекта командой `sh scripts/run_light_emulator.sh`, дождитесь сообщения «Эмулятор запущен», затем выберите уже запущенный `DriveNext_API_34` в Android Studio и нажмите **Run app**. Скрипт заменяет уже открытый экземпляр AVD и повторно включает защищённый DNS. При запуске AVD только кнопкой Run в Android Studio DNS может снова работать неверно. Закройте тяжёлые приложения перед запуском эмулятора.

Если Android показывает `Process system isn't responding` или `System UI isn't responding`, выберите **Wait**. При первом запуске Google Play Services может обновляться и компилироваться в фоне; дождитесь, пока загрузка процессора спадёт. Если окно повторяется после нескольких минут, остановите эмулятор и сделайте чистую загрузку: `sh scripts/run_light_emulator.sh -no-snapshot-load`.

Старый Pixel 10 Pro с Android 37 удалён, чтобы освободить место. В Android Studio открывайте именно папку `Разработка МП`. Для проекта создан отдельный Git-репозиторий; крупный локальный Gradle-кэш `mybisness_crm/` исключён из Git.

## Проверка лабораторной №2

Код и серверная настройка готовы, APK собирается. Пользователь подтвердил успешное прохождение регистрации в эмуляторе; этот сценарий включает загрузку обязательных фото и запись в `profiles`. Остаётся проверить повторный вход по email/паролю и завершение Google OAuth с возвратом в приложение. Открытие страницы Google-входа уже проверено.

1. На экране выбора нажмите «Войти». Проверьте, что кнопка входа неактивна при пустых полях, а пароль можно показать и скрыть.
2. Нажмите «Зарегистрироваться» и пройдите три шага. На шаге 1 проверьте ошибки email, несовпадающих паролей и обязательного согласия. На шаге 2 выберите дату рождения через календарь и пол. На шаге 3 выберите фото профиля при желании, фото прав и паспорта обязательно; фото можно выбрать из галереи или сделать камерой.
3. После успешной регистрации нажмите «Далее» и попадите на главный экран. Закройте и снова откройте приложение: действующая сессия ведёт сразу на главный экран.
4. Проверьте вход через email и пароль и кнопку Google. Если email confirmation включён, сначала подтвердите адрес по письму.

### Если регистрация и Google-вход не работают в эмуляторе

- Ошибка `Unable to resolve host` означает, что Android не смог определить IP-адрес сервера. Для этого AVD причина была в DNS роутера: он возвращал неверный IP Supabase. Если ошибка повторилась, запустите `sh scripts/run_light_emulator.sh` из корня проекта, дождитесь сообщения «Эмулятор запущен» и повторите действие. Скрипт включает защищённый DNS `dns.google`. После такого запуска Supabase отвечает в Chrome эмулятора.
- Для Google-входа в **Supabase → Authentication → Providers → Google** включите провайдер и укажите OAuth Client ID и Client Secret из Google Cloud. В Google Cloud разрешённый redirect URI: `https://jsjftmauqmuvanxwivnd.supabase.co/auth/v1/callback`.
- В **Supabase → Authentication → URL Configuration → Redirect URLs** добавьте `drivenext://auth-callback`. Этот адрес должен совпадать с deep link в AndroidManifest.
- Если AVD был очищен или создан заново, запустите его через `sh scripts/run_light_emulator.sh`, затем в терминале Android Studio выполните:

  ```bash
  adb shell settings put global private_dns_specifier dns.google
  adb shell settings put global private_dns_mode hostname
  ```

  После этого повторите действие в приложении. Проверка: откройте `https://jsjftmauqmuvanxwivnd.supabase.co/auth/v1/settings` в Chrome эмулятора. Ответ `No API key found in request` подтверждает, что соединение с сервером есть; запрос из браузера не содержит ключ приложения.

## Структура

| Файл | Назначение |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Разрешения, стартовая Activity, OAuth deep link и FileProvider для камеры. |
| `domain/Repositories.kt` | Контракты репозиториев, модель регистрационных данных и выбор стартового экрана. |
| `data/AndroidNetworkRepository.kt` | Проверка сети через ConnectivityManager. |
| `data/LocalRepositories.kt` | Прохождение онбординга и локальная сессия. |
| `data/SupabaseAuthRepository.kt` | Запросы к Supabase Auth, Storage и таблице профилей. |
| `presentation/MainViewModel.kt` | Переходы, проверка полей, ошибки, загрузка и обращения к доменному слою. |
| `presentation/MainActivity.kt` | Показ экранов, ViewPager2, системный пикер, камера и OAuth callback. |
| `presentation/AuthScreens.kt` | Экраны входа и трёх шагов регистрации, адаптивные формы. |
| `presentation/OnboardingSlideFragment.kt` | Слайды приветствия. |
| `presentation/IllustrationView.kt` | Масштабируемые иллюстрации. |
| `res/layout/`, `res/drawable/`, `res/values/` | Разметка, иконки и стили. |
