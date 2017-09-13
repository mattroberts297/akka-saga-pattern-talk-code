# Akka Saga Pattern

## Presentation Links

[Talk](https://www.youtube.com/watch?v=onv6xdyZ49s)

[Deck](https://drive.google.com/file/d/1t_UijDDjon_0ssD_CfAsLyGDiorSPaX2/view?usp=sharing)

## Getting Started

DynamoDB:

```bash
docker run -d --name dynamodb -p 8000:8000 peopleperhour/dynamodb

AWS_ACCESS_KEY_ID=me AWS_SECRET_ACCESS_KEY=secret aws dynamodb create-table --region us-east-1 --cli-input-json file://journal.json --endpoint-url http://localhost:8000

AWS_ACCESS_KEY_ID=me AWS_SECRET_ACCESS_KEY=secret aws dynamodb list-tables --region us-east-1 --endpoint-url http://localhost:8000
```

Usage:

```bash
curl --verbose "http://127.0.0.1:8080/register" -X POST -H 'Content-type: application/json' -d '{"email": "bob@example.com", "password": "p@ssw0rd"}'

curl --verbose "http://127.0.0.1:8080/users/<UUID>/change-email" -X POST -H 'Content-type: application/json' -d '{"oldEmail": "alice@example.com", "newEmail": "charlie@example.com"}'
```
