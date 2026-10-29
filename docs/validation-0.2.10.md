# Validação 0.2.10 — 29/10/2026

Dispositivo: Samsung Galaxy S25 (SM-S931B). Testes de interação executados no pacote separado `com.lamy.gymapp.validation`, sem gravar simulações no histórico real.

## Resultados

- 14 testes unitários: aprovados, sem falhas.
- Migração de sinais “+” antigos: mantém a última intenção válida sem alterar linhas históricas.
- Campo de carga: edição somente numérica, com vírgula decimal e persistência.
- Checks individuais: recolhem o card ao concluir a última série; reabertura mantém os checks.
- Duplo check: conclui/recolhe e desmarca/reabre; funciona em cards com uma ou várias séries.
- Progressão: o “+” continua marcado após desmarcar todas; a intenção pendente não é apagada.
- Outro treino: o mesmo exercício recebe a carga anterior e o aviso âmbar apenas na série sinalizada, com checks novos desmarcados.
- Registrar carga maior na próxima execução: resolve o aviso sem modificar o histórico anterior.
- Retomada da sessão, switch de lembrete, dias programados, seletor de horário e abertura pela notificação: aprovados.
- Capturas reais dos três estados inspecionadas visualmente: controles e avisos legíveis, sem sobreposição.

## Preservação dos dados

Antes da operação, foram guardados o banco, arquivos privados, preferências e o APK anterior, fora do Git. A migração Room 5 → 6 foi ensaiada sobre uma cópia. Após autorização explícita, a troca de assinatura foi realizada por reinstalação e restauração antes da primeira abertura.

Depois da migração e da abertura do app real, as contagens e os hashes SHA-256 de todas as tabelas de dados originais coincidiram com o backup final. A permissão de notificações previamente concedida foi restabelecida. As simulações permaneceram apenas na instalação QA, removida após os testes.

O instalador publicado é o mesmo instalado no dispositivo: versão 0.2.10, código 12. O README registra a troca de certificado e a exigência de preservar a nova chave para as próximas atualizações.
