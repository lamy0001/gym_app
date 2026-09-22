# Gym App

Aplicativo Android para organizar treinos de musculação e cardio, acompanhar cargas e preservar o histórico de evolução.

## Estado atual

A versão atual implementa o fluxo principal do aplicativo:

- Kotlin + Jetpack Compose.
- Tema claro.
- Menu inicial com carrossel de treinos.
- Abertura do treino por duplo toque no card.
- Cards de exercícios expandidos por padrão.
- Interface dos exercícios no layout 10 com paleta Floresta: cabeçalho destacado, séries em faixas alternadas, carga editável e ações de concluir/aumentar carga separadas visualmente.
- Persistência local com Room.
- Exercícios identificados globalmente para compartilhar cargas entre treinos.
- Carga utilizada anteriormente disponível como preset no próximo treino.
- Edição da carga durante a execução do exercício.
- Cinco treinos iniciais completos: Upper, Lower, Cardio, Upper 2 e Lower 2.
- Sessões e séries persistidas, com check e sinalização “+” de aumento de carga.
- Descanso editável por exercício e cronômetro acionável no card.
- Ao abrir um treino, o cabeçalho permite iniciar e finalizar a sessão; o botão Voltar do Android retorna ao menu sem encerrar o aplicativo.
- Editor de séries/descanso e exclusão de treinos com confirmação.
- Histórico real de sessões e gráfico de carga proporcional ao próprio exercício.
- Configurações persistidas, lembrete recorrente em dias úteis e horário editável.
- Exportação e restauração de backup JSON pelo seletor de arquivos do Android.

## Arquitetura

```text
Interface Compose
        ↓
ViewModel
        ↓
Room Database
        ↓
Dados locais persistentes
```

O banco separa:

- `exercises`: catálogo global de exercícios.
- `workouts`: treinos cadastrados.
- `workout_exercises`: exercícios vinculados a cada treino.
- `exercise_load_profiles`: última carga utilizada por exercício e série.
- `workout_sessions` e `session_sets`: sessões, séries concluídas e evolução.
- `app_settings`: preferências e lembretes.

Isso permite que, por exemplo, a “Puxada supinada” compartilhe a carga entre o Treino 1 e o Treino 3.

## Requisitos

- JDK 17 ou superior.
- Android SDK com API 35.
- Gradle Wrapper incluído no projeto.

## Compilar

No Windows PowerShell:

```powershell
./gradlew.bat assembleDebug
```

O APK será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Instalar em um dispositivo ou emulador

Com o Android Debug Bridge disponível:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Persistência e atualizações

Os dados do usuário devem permanecer no banco local durante atualizações do aplicativo. Novas versões devem usar migrações Room, sem apagar e recriar o banco.

Registros que devem ser preservados:

- Cargas utilizadas.
- Histórico de sessões.
- Séries e repetições realizadas.
- Treinos editados pelo usuário.
- Lembretes e dias programados.

## Backup

Na tela Configurações, “Exportar” gera um arquivo JSON com catálogo, treinos, cargas,
sessões, histórico e preferências. “Restaurar” faz upsert desses registros, preservando
dados locais que não estejam no arquivo.
