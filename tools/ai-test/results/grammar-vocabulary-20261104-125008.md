# Ket qua kiem thu Grammar va Vocabulary (Tuan 12, Ngay 7)

- Bat dau: 2026-11-04 12:50:08 UTC
- Backend: https://lori-25ej.onrender.com
- User thu: grammar.1793796608677@example.com (Free, sau do mua Premium bang mock-purchase)
- Premium gate (user Free): Dat
- Tu dong: 18/20 dat

| ID | Loai | HTTP | Thoi gian (s) | Tu dong | lessonId (thuc te / tham khao) |
|---|---|---|---|---|---|
| G1 | grammar | 200 | 3.7 | Dat | 1 / 1 |
| G2 | grammar | 200 | 3.1 | Dat | 3 / 3 |
| G3 | grammar | 200 | 2.6 | Dat | 7 / 7 |
| G4 | grammar | 200 | 3.5 | Dat | 11 / 11 |
| G5 | grammar | 200 | 4.8 | Dat | 19 / 19 |
| G6 | grammar | 200 | 3.0 | Dat | 17 / 17 |
| G7 | grammar | 200 | 5.2 | Dat | 14 / 14 |
| G8 | grammar | 200 | 4.7 | Dat | 3 / 3 |
| G9 | grammar | 200 | 3.4 | Dat | None / None |
| G10 | grammar | 200 | 5.6 | Dat | None / None |
| V1 | vocabulary | 200 | 5.4 | Dat |  |
| V2 | vocabulary | 503 | 6.4 | KHONG DAT |  |
| V3 | vocabulary | 200 | 6.1 | Dat |  |
| V4 | vocabulary | 503 | 7.6 | KHONG DAT |  |
| V5 | vocabulary | 200 | 4.7 | Dat |  |
| V6 | vocabulary | 200 | 3.9 | Dat |  |
| V7 | vocabulary | 200 | 4.1 | Dat |  |
| X1 | grammar | 400 | 0.4 | Dat |  |
| X2 | vocabulary | 400 | 0.3 | Dat |  |
| X3 | vocabulary | 400 | 0.3 | Dat |  |

## G1 (grammar)

Dau vao: `She don't like coffee in the morning.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "She doesn't like coffee in the morning.",
  "errors": [
    {
      "type": "Subject-verb agreement",
      "explanation": "Third person singular subject 'She' needs the verb form 'doesn't' instead of 'don't'."
    }
  ],
  "lessonId": 1
}
```

## G2 (grammar)

Dau vao: `Yesterday I go to school.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "Yesterday I went to school.",
  "errors": [
    {
      "type": "Subject-verb agreement",
      "explanation": "The verb 'go' should be in past tense 'went' because the sentence refers to a past action using 'Yesterday'."
    }
  ],
  "lessonId": 3
}
```

## G3 (grammar)

Dau vao: `I saw a elephant at the zoo.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "I saw an elephant at the zoo.",
  "errors": [
    {
      "type": "Article usage",
      "explanation": "Use 'an' before words starting with vowel sounds. 'Elephant' starts with a vowel sound (/ɪˈfænt/), so 'an' is correct instead of 'a'."
    }
  ],
  "lessonId": 7
}
```

## G4 (grammar)

Dau vao: `She has went to Hanoi twice.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "She has gone to Hanoi twice.",
  "errors": [
    {
      "type": "Verb Tense",
      "explanation": "Present Perfect requires past participle. 'Went' is simple past, but 'gone' is the correct past participle of 'go'."
    }
  ],
  "lessonId": 11
}
```

## G5 (grammar)

Dau vao: `If it will rain tomorrow, we will stay at home.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "If it rains tomorrow, we will stay at home.",
  "errors": [
    {
      "type": "First Conditional Structure",
      "explanation": "In the 'if' clause of a first conditional, use present simple (rains) instead of future (will rain). The main clause uses 'will' correctly."
    }
  ],
  "lessonId": 19
}
```

## G6 (grammar)

Dau vao: `The book was write by a famous author.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "The book was written by a famous author.",
  "errors": [
    {
      "type": "Verb form (past participle)",
      "explanation": "The verb 'write' should be in past participle form 'written' after 'was' in passive voice."
    }
  ],
  "lessonId": 17
}
```

## G7 (grammar)

Dau vao: `This house is more cheaper than that one.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "This house is cheaper than that one.",
  "errors": [
    {
      "type": "Redundant Comparative",
      "explanation": "Cheaper is the correct comparative form of cheap. 'More' is not needed before comparative adjectives that already have a standard form."
    }
  ],
  "lessonId": 14
}
```

## G8 (grammar)

Dau vao: `He go to school yesterday and buyed a new book.`

```json
{
  "type": "grammar_analysis",
  "correct": false,
  "correctedSentence": "He went to school yesterday and bought a new book.",
  "errors": [
    {
      "type": "Subject-verb agreement",
      "explanation": "Third person singular subject 'He' needs the verb 'goes' in present simple, but 'yesterday' indicates past tense, so 'went' is correct."
    },
    {
      "type": "Incorrect past tense",
      "explanation": "The past tense of 'buy' is 'bought', not 'buyed'."
    }
  ],
  "lessonId": 3
}
```

## G9 (grammar)

Dau vao: `She doesn't like coffee in the morning.`

```json
{
  "type": "grammar_analysis",
  "correct": true,
  "correctedSentence": "She doesn't like coffee in the morning.",
  "errors": [],
  "lessonId": null
}
```

## G10 (grammar)

Dau vao: `I have been studying English for three years.`

```json
{
  "type": "grammar_analysis",
  "correct": true,
  "correctedSentence": "I have been studying English for three years.",
  "errors": [],
  "lessonId": null
}
```

## V1 (vocabulary)

Dau vao: `improve`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "improve",
      "inDictionary": true,
      "ipa": "/ɪm.ˈpruːv/",
      "definitions": [
        {
          "pos": "V",
          "meaning": "Cải tiến, cải thiện, cải tạo, trau dồi (tư tưởng), mở mang (kiến thức... ).",
          "example": "to improve one's style of work"
        },
        {
          "pos": "V",
          "meaning": "Lợi dụng, tận dụng.",
          "example": "to improve the occasion"
        },
        {
          "pos": "V",
          "meaning": "Được cải tiến, được cải thiện, trở nên tốt hơn; tiến bộ.",
          "example": "to improve in health"
        }
      ],
      "explanation": "To make something better, such as skills, conditions, or performance. It can also mean to use something effectively or to progress.",
      "synonyms": [
        "enhance",
        "upgrade",
        "refine",
        "develop",
        "utilize"
      ],
      "antonyms": [
        "worsen",
        "deteriorate",
        "decline",
        "regress",
        "damage"
      ],
      "collocations": [
        "improve skills",
        "improve conditions",
        "improve performance",
        "improve efficiency",
        "improve health"
      ]
    }
  ],
  "difference": null
}
```

## V2 (vocabulary)

Dau vao: `affect, effect`

Khong dat:
- HTTP 503, ky vong 200

```json
{
  "status": 503,
  "message": "AI service unavailable"
}
```

## V3 (vocabulary)

Dau vao: `make vs do`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "make",
      "inDictionary": true,
      "ipa": "/meɪk/",
      "definitions": [
        {
          "pos": "N",
          "meaning": "Hình dáng, cấu tạo (của một vật); kiểu (quần áo); tầm vóc, dáng, tư thế (người).",
          "example": null
        },
        {
          "pos": "N",
          "meaning": "Sự chế tạo.",
          "example": "Vietnamese make"
        },
        {
          "pos": "N",
          "meaning": "Công tắc, cái ngắt điện.",
          "example": null
        },
        {
          "pos": "V",
          "meaning": "Làm, chế tạo.",
          "example": "to make bread"
        },
        {
          "pos": "V",
          "meaning": "Sắp đặt, xếp đặt, dọn, thu dọn, sửa soạn, chuẩn bị.",
          "example": "to make the bed"
        }
      ],
      "explanation": "To create, prepare, or cause something to happen.",
      "synonyms": [
        "create",
        "produce",
        "prepare"
      ],
      "antonyms": [
        "break",
        "destroy"
      ],
      "collocations": [
        "make a decision",
        "make a mistake",
        "make a meal"
      ]
    },
    {
      "word": "do",
      "inDictionary": true,
      "ipa": "/ˈduː/",
      "definitions": [
        {
          "pos": "V",
          "meaning": "Làm, thực hiện.",
          "example": "to do one's duty"
        },
        {
          "pos": "V",
          "meaning": "Làm, làm cho, gây cho.",
          "example": "to do somebody good"
        },
        {
          "pos": "V",
          "meaning": "Làm, học (bài...); giải (bài toán); dịch.",
          "example": "to do one's lessons"
        },
        {
          "pos": "V",
          "meaning": "Làm xong, xong, hết.",
          "example": null
        },
        {
          "pos": "V",
          "meaning": "Dọn, thu dọn, sắp xếp, thu xếp ngăn nắp, sửa soạn.",
          "example": "to do one's room"
        }
      ],
      "explanation": "To perform an action or task, often general or routine.",
      "synonyms": [
        "hair-do",
        "hairdo",
        "doh",
        "ut"
      ],
      "antonyms": [
        "don'ts"
      ],
      "collocations": [
        "do homework",
        "do the dishes",
        "do a favor"
      ]
    }
  ],
  "difference": "Make is used for creating, causing, or preparing something specific, while do is used for general actions or tasks. Use make for tangible results (e.g., make a plan) and do for activities (e.g., do exercise)."
}
```

## V4 (vocabulary)

Dau vao: `borrow / lend`

Khong dat:
- HTTP 503, ky vong 200

```json
{
  "status": 503,
  "message": "AI service unavailable"
}
```

## V5 (vocabulary)

Dau vao: `begin`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "begin",
      "inDictionary": true,
      "ipa": null,
      "definitions": [
        {
          "pos": "V",
          "meaning": "bắt đầu, khởi đầu, mở đầu",
          "example": null
        }
      ],
      "explanation": "To start or start doing something.",
      "synonyms": [
        "start"
      ],
      "antonyms": [
        "end",
        "finish",
        "stop"
      ],
      "collocations": [
        "begin a project",
        "begin to do something",
        "begin with",
        "beginning of",
        "beginning of the year"
      ]
    }
  ],
  "difference": null
}
```

## V6 (vocabulary)

Dau vao: `look up`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "look up",
      "inDictionary": false,
      "ipa": null,
      "definitions": [],
      "explanation": "To search for information or to raise your eyes to look at something.",
      "synonyms": [
        "search",
        "check",
        "query"
      ],
      "antonyms": [
        "look down",
        "ignore"
      ],
      "collocations": [
        "look up a word",
        "look up information",
        "look up a phone number",
        "look up the meaning",
        "look up a reference"
      ]
    }
  ],
  "difference": null
}
```

## V7 (vocabulary)

Dau vao: `written`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "written",
      "inDictionary": false,
      "ipa": null,
      "definitions": [],
      "explanation": "The past participle of 'write', used to describe something that has been written or recorded in text. It can also describe something expressed in writing rather than spoken.",
      "synonyms": [
        "typed",
        "composed",
        "recorded",
        "documented",
        "set down"
      ],
      "antonyms": [
        "spoken",
        "oral",
        "unwritten",
        "verbal",
        "improvised"
      ],
      "collocations": [
        "written word",
        "written language",
        "written instructions",
        "written record",
        "written exam"
      ]
    }
  ],
  "difference": null
}
```

## X1 (grammar)

Dau vao: `<b></b>`

```json
{
  "status": 400,
  "message": "Invalid input"
}
```

## X2 (vocabulary)

Dau vao: `apple, banana, orange`

```json
{
  "status": 400,
  "message": "Invalid input"
}
```

## X3 (vocabulary)

Dau vao: `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`

```json
{
  "status": 400,
  "message": "Invalid input"
}
```
