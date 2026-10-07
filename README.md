# Gym App

Aplicativo Android para organizar treinos de musculação e cardio, acompanhar cargas e preservar o histórico de evolução.

## Baixar e instalar

Baixe o instalador Android (APK) diretamente deste repositório:

**Versão mais recente: [Gym App v0.2.9](releases/GymApp-v0.2.9-debug.apk)**.

- [Gym App v0.1.0 (APK de teste)](releases/GymApp-v0.1.0-debug.apk)
- [Gym App v0.2.0 (APK de teste)](releases/GymApp-v0.2.0-debug.apk)
- [Gym App v0.2.1 (APK de teste)](releases/GymApp-v0.2.1-debug.apk)
- [Gym App v0.2.2 (APK de teste)](releases/GymApp-v0.2.2-debug.apk)
- [Gym App v0.2.3 (APK de teste)](releases/GymApp-v0.2.3-debug.apk)
- [Gym App v0.2.4 (APK de teste)](releases/GymApp-v0.2.4-debug.apk)
- [Gym App v0.2.5 (APK de teste)](releases/GymApp-v0.2.5-debug.apk)
- [Gym App v0.2.6 (APK de teste)](releases/GymApp-v0.2.6-debug.apk)
- [Gym App v0.2.7 (APK de teste)](releases/GymApp-v0.2.7-debug.apk)
- [Gym App v0.2.8 (APK de teste)](releases/GymApp-v0.2.8-debug.apk)
- [Gym App v0.2.9 (APK de teste)](releases/GymApp-v0.2.9-debug.apk)

É uma compilação de depuração para instalação direta; o Android pode solicitar autorização para instalar apps desta fonte. O APK não é distribuído pela Play Store.

## Estado atual

A versão atual implementa o fluxo principal do aplicativo:

- Kotlin + Jetpack Compose.
- Tema claro.
- Menu inicial com carrossel centralizado no treino do dia: os treinos seguem a ordem dos dias selecionados em Configurações (por padrão, segunda a sexta). Em dias de descanso, mostra o próximo treino. Os demais continuam acessíveis deslizando.
- Abertura do treino por duplo toque no card.
- Cards de exercícios expandidos por padrão.
- Períodos de treino independentes, com treinos separados por ciclo e histórico consultável por período.
- Criação de um novo período com opção de copiar os treinos do ciclo atual; a cópia mantém exercícios, séries, repetições e descansos editáveis sem alterar o ciclo anterior.
- Sessões gravadas no período em que foram iniciadas; treinos e evolução podem ser consultados ao alternar períodos.
- O botão Voltar do Android segue a navegação interna: retorna ao menu ao sair de treinos/configurações/histórico e volta à lista de treinos ao sair do editor.
- No histórico, cada exercício aparece em um bloco com seu gráfico logo abaixo. O gráfico resume a maior carga por sessão, usa escala própria para cada exercício e destaca a sessão mais recente.
- As métricas do histórico contam apenas sessões finalizadas com ao menos uma série concluída. A frequência considera os dias programados em Configurações na semana atual, sem contar duas sessões no mesmo dia como dois dias.
- Sessão em andamento e séries marcadas são restauradas quando o app é reaberto; voltar ao menu não finaliza o treino.
- Cada exercício tem anotação persistente, exibida sob o grupo muscular e o descanso. O cabeçalho usa um tom mais escuro e permite marcar todas as séries de uma vez.
- Concluir a última série individualmente, pelo check ou pelo “+”, recolhe o card automaticamente. O duplo check marca todas as séries e recolhe o card; reabri-lo mantém os checks. O comando aparece também em cards de uma só série.
- Os cinco treinos padrão foram revistos com base nas imagens recebidas, incluindo ordem, séries, repetições por série, cargas planejadas e dados da caminhada.
- Cargas continuam compartilhadas por exercício entre períodos e treinos, aparecendo como preset ao reutilizar um exercício.
- Interface dos exercícios no layout 10 com paleta Floresta: cabeçalho destacado, séries em faixas alternadas, carga editável e ações de concluir/aumentar carga separadas visualmente.
- Persistência local com Room.
- Exercícios identificados globalmente para compartilhar cargas entre treinos.
- Carga utilizada anteriormente disponível como preset no próximo treino.
- Edição da carga durante a execução do exercício.
- O campo de carga edita somente números, aceita vírgula ou ponto decimal e mostra “kg” como unidade separada. A carga editada atualiza a sessão em andamento e o preset do exercício.
- Cinco treinos iniciais completos: Upper, Lower, Cardio, Upper 2 e Lower 2.
- Sessões e séries persistidas, com check e sinalização “+” de aumento de carga.
- Descanso editável por exercício e cronômetro acionável no card.
- Ao abrir um treino, o cabeçalho permite iniciar e finalizar a sessão; o botão Voltar do Android retorna ao menu sem encerrar o aplicativo.
- Editor de séries/descanso e exclusão de treinos com confirmação.
- Histórico real de sessões e gráfico de carga proporcional ao próprio exercício.
- Configurações persistidas: ativar/desativar lembretes, selecionar os dias e escolher o horário em um seletor. Tocar na notificação abre o app. Os lembretes são reagendados após reiniciar o celular, atualizar o app ou alterar o relógio/fuso. A entrega pode ser adiada pelas restrições de bateria do Android.
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
- `workout_sessions` e `session_sets`: sessões vinculadas ao período em que ocorreram, séries concluídas e evolução.
- `training_periods` e `workout_period_links`: ciclos de treino e treinos independentes de cada ciclo.
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

A versão 0.2.9 mantém o esquema Room 5. A instalação usa `adb install -r` e a mesma chave de assinatura da versão anterior. Antes e depois da atualização, `scripts/device_data_snapshot.py` pode registrar uma cópia local do banco e hashes por tabela para verificar a preservação dos dados. Backups ficam em `backup-device-data/`, ignorado pelo Git; chaves de assinatura não são publicadas.

## Validar as interações no Android

Os testes de UI usam uma instalação separada para manter os dados reais fora da simulação:

```powershell
./gradlew.bat '-PqaApplicationId=com.lamy.gymapp.validation' testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.lamy.gymapp.validation android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.lamy.gymapp.validation.test/com.lamy.gymapp.GymUiTestRunner
```

O runner recusa executar fora do ID de validação. Ele verifica edição numérica da carga, checks individuais, recolhimento após a última série, duplo check, card de uma série, restauração da sessão, controles de lembrete, seletor de horário e abertura do app pela notificação a partir da tela inicial do celular. Após validar, remova somente os pacotes `.validation` e `.validation.test` e recompile sem `qaApplicationId` para gerar o APK final.

### Períodos de treino

Em **Meus treinos**, o cabeçalho mostra o período ativo. **Trocar / ver períodos** permite reabrir um ciclo anterior. **+ Novo período** cria um ciclo separado e, por padrão, copia os treinos do período atual para que exercícios, séries, repetições e descansos possam ser ajustados sem modificar o ciclo anterior. Também é possível iniciar o novo período vazio.

Treinos criados pertencem ao período ativo. Remover um treino o retira somente daquele período; os ciclos anteriores e sessões já registradas permanecem. Ao iniciar uma sessão, o app grava o período junto com treino, horário e séries. No **Histórico**, o seletor filtra frequência e evolução por período ou mostra todos os ciclos. A carga continua compartilhada por ID de exercício entre treinos e períodos.

A migração Room 3 → 4 adiciona a referência de período às sessões existentes e as associa ao ciclo que já estava ativo, sem remover cargas, treinos ou histórico.

Registros que devem ser preservados:

- Cargas utilizadas.
- Histórico de sessões.
- Séries e repetições realizadas.
- Treinos editados pelo usuário.
- Períodos, vínculos entre treinos e ciclos, e o período associado a cada sessão.
- Lembretes e dias programados.

## Backup

Na tela Configurações, “Exportar” gera um arquivo JSON com catálogo, treinos, cargas,
sessões, histórico e preferências. “Restaurar” faz upsert desses registros, preservando
dados locais que não estejam no arquivo.
