
## Харнесс: вкладка «ZCode»

Мобильный пульт к headless-агенту (ZCode Protocol через мост на сервере):

- Вкладка **«ZCode»** рядом с чатом Саши: выбор проекта → задача → стриминг шагов агента → кнопка «Стоп»
- Серверный канал: `http://144.31.207.192/harness/` (nginx VPS) → ssh-туннель → `zcode-bridge` на Коренёво → `zcode.cjs app-server`
- Токен моста: экран настройки (шифрованное хранилище), в debug-сборке — `ZCODE_TOKEN` из `local.properties`
- Клиент: OkHttp (IPv4-first DNS, HTTP/1.1, CLEARTEXT+TLS1.2 connectionSpecs)
- Подробности: `harness/HARNESS-SPEC.md` в репозитории sasha-assistant
