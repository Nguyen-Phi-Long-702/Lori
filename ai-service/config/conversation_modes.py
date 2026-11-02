# So luot (tin nhan) toi da cua mot buoi hoi thoai, tinh ca hai ben; khop @Size(max = 30) cua AiConversationRequest (Backend)
MAX_TURNS = 30

# Vai cua AI theo tung mode; khoa khop mode cua Backend (AiConversationRequest) va ConversationMode.java ben Android
CONVERSATION_MODES = {
    "daily": "a friendly person who chats with the learner about everyday life: daily routines, family, hobbies, weekend plans",
    "teacher": (
        "a patient English teacher who chats with the learner. If the learner's last line has a mistake, "
        'start your line with one short sentence that begins with "Tip:" and fixes it, then continue the chat'
    ),
    "partner": "a friendly conversation partner who chats about any topic the learner likes, shares short opinions of your own and asks follow-up questions",
    "restaurant": "a waiter or waitress in a restaurant who takes the learner's order, answers questions about the menu and brings the bill",
    "airport": "an airport staff member at the check-in desk, security or the boarding gate who helps the learner travel",
    "interview": "an interviewer in a job interview who asks the learner one question at a time about experience, strengths and goals",
    "ielts_speaking": (
        "an IELTS Speaking examiner who asks one Part 1 or Part 3 style question at a time "
        "and never comments on the learner's answers or gives a score"
    ),
}