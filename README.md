# Quiz Bot with LLM Question Generator

An intelligent Telegram bot (<link>) for learning and running quizzes powered by AI (GigaChat).

## Features

- **Quizzes**: random question selection with progress tracking — correctly answered questions are never repeated.
- **AI Generation**: automatic creation of questions, explanations, and hints via GigaChat, plus adaptive difficulty updates.
- **Groups**: create groups, invite members, set group schedules, and view shared statistics.
- **Scheduling**: automatic question delivery to users and groups via cron expressions.
- **Admin panel**: full management of questions and topics directly through Telegram.
- **REST API**: endpoints for health monitoring and user listing.

---

## Commands

### For all users

- `\help` — list available commands for your role.
- `\quiz start [topic1] [topic2]...` — start a quiz (optionally filter by topics).
- `\cancel` — end the current quiz.
- `\get questions` — list all topics with question counts (for regular users — includes correct answer counts).
- `\score` — view overall statistics (total answers, percentage correct).
- `\score <topic>` — statistics for a specific topic.
- `\score reset` — reset your progress (with confirmation).
- `\group leave` — leave a group.
- `\group score` — statistics for your groups.

### For administrators

#### Topics
- `\add tag <name>` — add a new topic.
- `\update tag <old_name> <new_name>` — rename a topic.
- `\delete tag <name>` — delete a topic (only if it has no questions).

#### Questions
- `\add question <topic1> [topic2]...` — add a question manually (step-by-step dialog).
- `\add question gen <topic1> [topic2]...` — generate a question via AI.
- `\get questions` — list all topics with question counts.
- `\get questions all` — all questions with ID, difficulty, answers, and topics.
- `\get questions <topic>` — questions for a specific topic.
- `\update question <ID>` — update a question step by step.
- `\update difficulty` — trigger adaptive difficulty recalculation via AI.
- `\delete question <ID>` — delete a question by ID.
- `\delete question <topic>` — delete all questions for a topic (with confirmation).
- `\delete question all` — delete all questions (with confirmation).

#### Scheduling
- `\schedule set <cron>` — set up automatic delivery (e.g. `0 12 * * *`).
- `\schedule off` — disable automatic delivery.
- `\schedule status` — show current schedule state.

#### Groups
- `\group create <name>` — create a new group.
- `\group invite <group_ID> <user_ID>` — invite a user to a group.
- `\group exclude <group_ID> <user_ID>` — remove a user from a group.
- `\group list` — list all groups with their members.
- `\group score` — group statistics.
- `\group delete <group_ID>` — delete a group (with confirmation).
- `\group schedule set <group_ID> <cron>` — set a schedule for a group.
- `\group schedule off <group_ID>` — disable a group's schedule.

#### Users
- `\upgrade <ID>` — promote a user to administrator.

---

## Deployment

### Prerequisites

- Docker and Docker Compose
- Telegram bot token (via @BotFather)
- GigaChat API key

### Running with Docker Compose

1. Create a `.env` file in the project root:
   ```env
   TELEGRAM_BOT_TOKEN=your_token
   GIGACHAT_API_KEY=your_key
   ADMIN_CHAT_IDS=your_telegram_id
   JWT_SECRET=your_api_secret
   ```

2. Start the project:
   ```bash
   docker-compose up -d
   ```

The application will be available on port `8080`.

### Docker Hub

The project image is published on Docker Hub (<link>). You can run it without building locally:

```bash

```

Or run it directly with `docker run`:

```bash
docker run -d \
  -e TELEGRAM_BOT_TOKEN=your_token \
  -e GIGACHAT_API_KEY=your_key \
  -e ADMIN_CHAT_IDS=your_telegram_id \
  -e JWT_SECRET=your_secret \
  -p 8080:8080 \
  <dockerhub-username>/quiz-bot:latest
```

---

## REST API

- `GET /healthcheck` — public endpoint. Returns `status` (`UP`/`DOWN`) and a list of authors.
- `GET /users` — list of registered users with ID, role, and registration date. Requires `X-API-KEY` header. Accessible to administrators only.

---

## Tech Stack

* Java 25
* Spring Framework 7
* Reactive Spring (WebFlux)
* Data MongoDB Reactive
* Spring REST Docs
* Gradle
* MongoDB
* Docker
* Docker Compose
* Telegram API
* GigaChat
* JUnit 5