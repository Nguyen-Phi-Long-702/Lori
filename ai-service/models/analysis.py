from typing import Literal

from pydantic import BaseModel, Field


class QuizHistory(BaseModel):
    correctCount: int
    incorrectCount: int
    # Trang thai do Learning Engine quyet dinh: learning / reviewing / mastered
    status: str


class QuizAnalysisRequest(BaseModel):
    quizType: Literal["vocabulary", "grammar"]
    title: str
    # Ket qua lan lam gan nhat, do Backend cham
    correctCount: int
    incorrectCount: int
    accuracyPercent: int
    # Ket qua cong don cua chu de nay, None neu Backend chua co
    history: QuizHistory | None = None
    # {id: ten} cac bai ngu phap cua Lori; model chi duoc lien ket toi id trong danh sach nay
    lessons: dict[int, str]
    learning_context: dict | None = Field(default=None, alias="learningContext")


class ExamStat(BaseModel):
    name: str
    correctCount: int
    totalCount: int
    accuracyPercent: int


class ExamPart(ExamStat):
    skill: str


class ExamWrongQuestion(BaseModel):
    part: str
    question: str
    yourAnswer: str | None = None
    correctAnswer: str


class ExamAnalysisRequest(BaseModel):
    examType: Literal["TOEIC", "IELTS"]
    title: str
    # Diem chinh thuc do Backend tinh: TOEIC 10-990, IELTS band 0-9
    totalScore: float
    listeningScore: float
    readingScore: float
    listeningCorrect: int
    listeningTotal: int
    readingCorrect: int
    readingTotal: int
    timeSpentSeconds: int
    parts: list[ExamPart]
    questionTypes: list[ExamStat]
    wrongQuestions: list[ExamWrongQuestion]
    lessons: dict[int, str]
    learning_context: dict | None = Field(default=None, alias="learningContext")