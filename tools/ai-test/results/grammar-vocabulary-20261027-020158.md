# Ket qua kiem thu Grammar va Vocabulary (Tuan 12, Ngay 7)

- Bat dau: 2026-10-27 02:01:58 UTC
- Backend: https://lori-25ej.onrender.com
- User thu: grammar.1793066518660@example.com (Free, sau do mua Premium bang mock-purchase)
- Premium gate (user Free): Dat
- Tu dong: 19/20 dat

| ID | Loai | HTTP | Thoi gian (s) | Tu dong | lessonId (thuc te / tham khao) |
|---|---|---|---|---|---|
| G1 | grammar | 200 | 4.8 | Dat | 1 / 1 |
| G2 | grammar | 200 | 3.4 | Dat | 3 / 3 |
| G3 | grammar | 200 | 4.7 | Dat | 7 / 7 |
| G4 | grammar | 200 | 3.3 | Dat | 11 / 11 |
| G5 | grammar | 200 | 8.4 | Dat | 19 / 19 |
| G6 | grammar | 200 | 4.7 | Dat | 17 / 17 |
| G7 | grammar | 200 | 3.2 | Dat | 14 / 14 |
| G8 | grammar | 200 | 5.2 | Dat | 3 / 3 |
| G9 | grammar | 200 | 4.3 | Dat | None / None |
| G10 | grammar | 200 | 3.4 | Dat | None / None |
| V1 | vocabulary | 200 | 5.4 | Dat |  |
| V2 | vocabulary | 503 | 6.2 | KHONG DAT |  |
| V3 | vocabulary | 200 | 8.2 | Dat |  |
| V4 | vocabulary | 200 | 7.3 | Dat |  |
| V5 | vocabulary | 200 | 17.5 | Dat |  |
| V6 | vocabulary | 200 | 3.9 | Dat |  |
| V7 | vocabulary | 200 | 2.9 | Dat |  |
| X1 | grammar | 400 | 1.0 | Dat |  |
| X2 | vocabulary | 400 | 0.9 | Dat |  |
| X3 | vocabulary | 400 | 0.8 | Dat |  |

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
      "explanation": "Third person singular subject 'She' requires the verb to have 's' ending. 'Don't' is used for plural subjects, but 'doesn't' is correct for 'She'."
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
      "explanation": "The time adverb 'Yesterday' requires past tense. 'Go' should be 'went' to match the past simple tense."
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
      "type": "a/an",
      "explanation": "Use 'an' before words starting with a vowel sound. 'Elephant' starts with a vowel sound, so 'an' is correct."
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
      "explanation": "In first conditional sentences, the 'if' clause uses present simple tense, not future tense. 'Will' in the 'if' clause is incorrect."
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
      "type": "Verb Form",
      "explanation": "The verb 'write' should be in past participle form 'written' after 'was' to form the passive voice."
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
      "type": "Double Comparative",
      "explanation": "Cheaper is already the comparative form of cheap. 'More' is unnecessary here."
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
      "explanation": "Third person singular subject 'He' requires 'goes' in present simple, but 'yesterday' indicates past tense, so 'went' is correct."
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
      "explanation": "To make something better, upgrade, or progress. It can also mean to utilize something effectively.",
      "synonyms": [
        "upgrade",
        "enhance",
        "refine",
        "progress",
        "develop"
      ],
      "antonyms": [
        "worsen",
        "deteriorate",
        "regress",
        "degrade",
        "stay the same"
      ],
      "collocations": [
        "improve skills",
        "improve performance",
        "improve health",
        "improve relationships",
        "improve efficiency"
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
      "explanation": "To create, produce, or cause something to happen. It is often used for physical or abstract creation.",
      "synonyms": [
        "create",
        "produce",
        "cause"
      ],
      "antonyms": [
        "destroy",
        "prevent"
      ],
      "collocations": [
        "make a decision",
        "make a mistake",
        "make progress",
        "make a noise",
        "make a plan"
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
      "explanation": "To perform an action or task. It is used for general activities, especially routine or repeated actions.",
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
        "do research",
        "do a favor",
        "do the job"
      ]
    }
  ],
  "difference": "Make is used for creating, causing, or producing something (e.g., 'make a plan'). Do is used for general actions or tasks (e.g., 'do homework'). Make often refers to the result of an action, while do focuses on the action itself."
}
```

## V4 (vocabulary)

Dau vao: `borrow / lend`

```json
{
  "type": "vocabulary_analysis",
  "words": [
    {
      "word": "borrow",
      "inDictionary": true,
      "ipa": "bŏrʹō",
      "definitions": [
        {
          "pos": "V",
          "meaning": "Vay, mượn.",
          "example": "to borrow at interest"
        },
        {
          "pos": "V",
          "meaning": "Theo, mượn.",
          "example": "to borrow an idea"
        }
      ],
      "explanation": "To take something from someone with the intention to return it.",
      "synonyms": [
        "take",
        "use"
      ],
      "antonyms": [
        "lend"
      ],
      "collocations": [
        "borrow money",
        "borrow a book",
        "borrow time",
        "borrow clothes",
        "borrow ideas"
      ]
    },
    {
      "word": "lend",
      "inDictionary": true,
      "ipa": "/ˈlɛnd/",
      "definitions": [
        {
          "pos": "V",
          "meaning": "Cho vay, cho mượn.",
          "example": "to lend money at interest"
        },
        {
          "pos": "V",
          "meaning": "Thêm phần, thêm vào.",
          "example": "to lend enchantment to..."
        }
      ],
      "explanation": "To give something to someone to use temporarily.",
      "synonyms": [
        "give",
        "offer"
      ],
      "antonyms": [
        "borrow"
      ],
      "collocations": [
        "lend money",
        "lend a hand",
        "lend an ear",
        "lend support",
        "lend time"
      ]
    }
  ],
  "difference": "Borrow means taking something from someone, while lend means giving something to someone. Use 'borrow' when you need to take (e.g., 'I borrow a book'), and 'lend' when you give (e.g., 'I lend a book')."
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
      "explanation": "bắt đầu, khởi đầu, mở đầu. Động từ chỉ việc bắt đầu một hành động hoặc sự việc.",
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
        "begin with",
        "begin today",
        "begin to do something",
        "begin the process"
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
      "explanation": "To search for information or to raise your eyes to see something",
      "synonyms": [
        "search",
        "lookup",
        "raise eyes"
      ],
      "antonyms": [
        "look down",
        "ignore",
        "lower eyes"
      ],
      "collocations": [
        "look up a word",
        "look up information",
        "look up at the sky",
        "look up a number",
        "look up details"
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
      "explanation": "The past participle of 'write', used to describe something that has been written or recorded in text.",
      "synonyms": [
        "typed",
        "composed",
        "recorded",
        "noted",
        "documented"
      ],
      "antonyms": [
        "spoken",
        "oral",
        "unwritten",
        "silent",
        "unrecorded"
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
