# Gym App

Aplicativo Android para organizar treinos de musculação e cardio, acompanhar cargas e preservar o histórico de evolução.

## Estado atual

A primeira fundação do aplicativo já está criada:

- Kotlin + Jetpack Compose.
- Tema claro.
- Menu inicial com carrossel de treinos.
- Abertura do treino por duplo toque no card.
- Cards de exercícios expandidos por padrão.
- Persistência local com Room.
- Exercícios identificados globalmente para compartilhar cargas entre treinos.
- Carga utilizada anteriormente disponível como preset no próximo treino.
- Edição da carga durante a execução do exercício.

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

## Próximos passos

1. Implementar histórico de sessões.
2. Registrar checkboxes de série e sinalização de aumento de carga.
3. Adicionar edição completa de treinos.
4. Adicionar descanso por exercício.
5. Implementar lembretes e dias programados.
6. Criar histórico com gráficos de frequência e carga por exercício.
7. Adicionar exportação e restauração de backup.
