# 📅 Agendamento API

![CI](https://github.com/Gabriel-Pacheco-K/agendamento-api/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)

API REST de agendamento para salões, barbearias, clínicas e consultórios. Cadastra profissionais com o seu expediente e os serviços oferecidos, calcula os **horários livres** e **impede que o mesmo horário seja reservado duas vezes**, mesmo quando várias pessoas tentam reservar ao mesmo tempo.

---

## 🎯 O problema central: reservas simultâneas

O jeito ingênuo de agendar é "verificar se o horário está livre e então gravar". Esse jeito falha quando dois clientes clicam em "reservar" no mesmo instante:

```
Cliente A: o horário das 14:00 está livre? ✅ sim
Cliente B: o horário das 14:00 está livre? ✅ sim   ← A ainda não gravou
Cliente A: grava a reserva
Cliente B: grava a reserva                           ← duas pessoas no mesmo horário ❌
```

**A solução:** antes de verificar, a reserva **trava a linha do profissional no banco** (`SELECT ... FOR UPDATE`, via `@Lock(PESSIMISTIC_WRITE)`). Reservas para o mesmo profissional passam a ser feitas uma de cada vez, e a segunda já encontra o horário ocupado. Reservas para profissionais diferentes continuam acontecendo em paralelo.

**Como isso foi comprovado:**
- Um teste dispara **10 threads ao mesmo tempo** para o mesmo horário e verifica que só 1 reserva é aceita.
- **Removendo a trava, o mesmo teste falha com 10 reservas gravadas**, o que mostra que o teste realmente detecta o problema.
- Com PostgreSQL real, **15 requisições HTTP em paralelo** resultaram em 1 `201 Created` e 14 `409 Conflict`.

## ✨ Funcionalidades

- **Profissionais** com expediente semanal, incluindo vários períodos por dia (por exemplo, uma pausa para o almoço)
- **Serviços** com duração e preço
- **Horários disponíveis**: calcula os horários livres de um dia para um serviço, considerando o expediente, os agendamentos existentes e a antecedência mínima
- **Agendamento** com validação: horário dentro do expediente, sem sobreposição, no futuro e dentro do limite de dias à frente
- **Ciclo de vida do agendamento**, como uma máquina de estados:

```
SCHEDULED ──▶ CONFIRMED ──▶ COMPLETED
    │             │    └──▶ NO_SHOW
    └─────────────┴──▶ CANCELLED
```

- **Regras configuráveis** no `application.yml`, sem mudar código: antecedência mínima, prazo de cancelamento, intervalo entre horários e limite de dias à frente
- **Erros no padrão RFC 7807** (Problem Details) e documentação interativa no Swagger

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (records, `switch` expressions, `List.getFirst`) |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) |
| Banco de dados | PostgreSQL 16 + Flyway |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5 (testes parametrizados), Mockito, AssertJ, MockMvc, H2, testes concorrentes com `ExecutorService` |
| Infra | Docker (multi-stage), Docker Compose, GitHub Actions |

## 🧠 Decisões técnicas

- **Trava pessimista em vez de otimista.** A versão otimista (`@Version`) detectaria conflitos na própria entidade, mas aqui o conflito é entre **agendamentos diferentes** que disputam o mesmo intervalo. Travar o profissional coloca as reservas dele em fila de forma simples e correta.
- **Sobreposição de intervalos**: `[a, b)` e `[c, d)` se sobrepõem quando `a < d` e `c < b`. Um corte que termina às 10:00 não conflita com outro que começa às 10:00.
- **Cálculo de horários em O(n + m)**: os agendamentos chegam ordenados por início, e um ponteiro avança junto com os horários. Não é preciso comparar cada horário com todos os agendamentos.
- **Máquina de estados no enum**: as transições permitidas ficam em um único lugar (`AppointmentStatus.canChangeTo`), e não espalhadas em vários `if/else`.
- **`Clock` injetável no fuso do estabelecimento**: as regras que dependem da hora atual, como o prazo de cancelamento, são testadas com relógio fixo, sem depender do momento em que o teste roda.
- **Defesa em profundidade**: além das validações no Java, o banco também garante `start < end`, duração positiva e preço não negativo com `CHECK` constraints.
- **Índice em `(professional_id, start_at)`**: cobre as duas consultas mais frequentes, a busca de conflito e a agenda do dia.

## 🚀 Como executar

```bash
git clone https://github.com/Gabriel-Pacheco-K/agendamento-api.git
cd agendamento-api
docker compose up --build
```

A documentação fica em **http://localhost:8080/swagger-ui.html**.

Para rodar localmente (Java 21 e Maven): `docker compose up -d db` e, em seguida, `mvn spring-boot:run`.

## 📖 Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/professionals` | Cadastra um profissional |
| `GET` | `/api/professionals` · `/api/professionals/{id}` | Lista ou busca profissionais |
| `PUT` | `/api/professionals/{id}/working-hours` | Define o expediente semanal |
| `GET` | `/api/professionals/{id}/availability?serviceId=&date=` | **Horários livres** no dia |
| `POST` | `/api/services` · `GET /api/services` | Cadastra ou lista serviços |
| `POST` | `/api/appointments` | Agenda (`409` se o horário estiver ocupado) |
| `GET` | `/api/appointments?professionalId=&date=` | Agenda do profissional no dia |
| `PATCH` | `/api/appointments/{id}/confirm` | Confirma |
| `PATCH` | `/api/appointments/{id}/cancel` | Cancela (até 2h antes) |
| `PATCH` | `/api/appointments/{id}/complete` | Conclui (depois do início) |
| `PATCH` | `/api/appointments/{id}/no-show` | Registra falta |

### Exemplo

```bash
# Expediente: segunda, das 9h às 12h e das 13h às 18h
curl -X PUT localhost:8080/api/professionals/1/working-hours -H "Content-Type: application/json" -d '{
  "periods": [
    {"dayOfWeek": "MONDAY", "start": "09:00", "end": "12:00"},
    {"dayOfWeek": "MONDAY", "start": "13:00", "end": "18:00"}
  ]}'

# Horários livres para um corte de 30 min
curl "localhost:8080/api/professionals/1/availability?serviceId=1&date=2026-10-19"
# => {"date":"2026-10-19","durationMinutes":30,"slots":["09:00:00","09:15:00", ... "11:30:00","13:00:00", ... "17:30:00"]}

# Agendar
curl -X POST localhost:8080/api/appointments -H "Content-Type: application/json" -d '{
  "professionalId": 1, "serviceId": 1, "startAt": "2026-10-19T14:00",
  "customerName": "Maria Silva", "customerPhone": "(48) 99999-0000"}'
```

Resposta ao tentar agendar um horário ocupado:

```json
{
  "title": "Horário indisponível",
  "status": 409,
  "detail": "O horário 14:00 já está ocupado para Ana Souza."
}
```

## 🧪 Testes

```bash
mvn verify
```

São **26 testes**:

- **Concorrência**: 10 reservas simultâneas para o mesmo horário, das quais só 1 é aceita
- **Horários disponíveis**: o serviço precisa caber inteiro no período, horários adjacentes continuam livres, a antecedência mínima é respeitada e um serviço não atravessa o almoço
- **Máquina de estados**: 11 transições testadas de forma parametrizada
- **Prazos com relógio fixo**: cancelamento até 2h antes (com 1h59 de antecedência é recusado) e conclusão só depois do início
- **Integração** (MockMvc + H2): agendar remove os horários afetados, sobreposição é recusada (409), cancelar libera o horário, expediente e almoço são respeitados, além de validações e erros

O GitHub Actions executa os testes e o build da imagem Docker a cada push.

## 🗺️ Próximos passos

- [ ] Autenticação com papéis (administrador, profissional e cliente)
- [ ] Lembretes por WhatsApp ou e-mail no dia anterior
- [ ] Bloqueios de agenda, como férias e feriados
- [ ] Constraint de exclusão do PostgreSQL (`EXCLUDE USING gist`) como segunda barreira contra sobreposição

---

Desenvolvido por **Gabriel Pacheco Kiesel**: [Portfólio](https://gabriel-pacheco-k.github.io) · [LinkedIn](https://www.linkedin.com/in/gabriel-pacheco-3b637a362/) · [GitHub](https://github.com/Gabriel-Pacheco-K)
