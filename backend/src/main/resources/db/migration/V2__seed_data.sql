-- =====================================================================
-- Seed / sample data for AssessWise (a fictional online-assessment company).
--
-- What this seeds:
--   * 16 curated knowledge snippets, each with a REAL, publicly reachable source URL.
--   * 6 sample processes with their current-state activities, roles, systems and the
--     pain points already known to the business.
--
-- What this deliberately does NOT seed:
--   * Any ai_opportunity, future_activity or ai_intervention row. Every process starts at
--     status CURRENT_ONLY so that the demo runs the real pipeline live on them — nothing about
--     the future state is pre-baked.
--
-- These six processes exist to make the demo concrete. The pipeline has no knowledge of them:
--   a process created through the UI thirty seconds ago takes an identical code path.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Roles (shared lookup)
-- ---------------------------------------------------------------------
INSERT INTO role (id, name, created_at)
SELECT gen_random_uuid(), name, now()
FROM (VALUES
    ('Exam Coordinator'),
    ('Subject Matter Expert'),
    ('Assessment Designer'),
    ('Content Author'),
    ('QA Analyst'),
    ('Psychometrician'),
    ('Registration Officer'),
    ('Support Executive'),
    ('Support Lead'),
    ('Proctor'),
    ('Integrity Review Panel'),
    ('Evaluation Coordinator'),
    ('Evaluator'),
    ('Moderator'),
    ('Certification Officer'),
    ('Programme Head')
) AS seed(name);

-- ---------------------------------------------------------------------
-- Systems and tools (shared lookup)
-- ---------------------------------------------------------------------
INSERT INTO system_tool (id, name, type, created_at)
SELECT gen_random_uuid(), name, type, now()
FROM (VALUES
    ('Assessment Platform',    'Core Platform'),
    ('Question Bank',          'Content Repository'),
    ('Authoring Tool',         'Authoring'),
    ('Proctoring Engine',      'Proctoring'),
    ('Candidate Portal',       'Portal'),
    ('Evaluation Console',     'Evaluation'),
    ('Analytics Dashboard',    'Analytics'),
    ('Certificate Generator',  'Document Generation'),
    ('Ticketing System',       'Service Desk'),
    ('Knowledge Base',         'Knowledge Management'),
    ('Email',                  'Communication'),
    ('Spreadsheet',            'Productivity'),
    ('Case Management System', 'Case Management')
) AS seed(name, type);

-- ---------------------------------------------------------------------
-- Curated knowledge snippets (the research / evidence layer)
--
-- Every snippet_text below is a short paraphrase written for this project, not a copied
-- excerpt, and every source_url was checked to resolve at the recorded retrieval date.
-- ---------------------------------------------------------------------
INSERT INTO knowledge_snippet (id, title, snippet_text, source_url, source_type, publisher, tags, retrieved_at, created_at)
SELECT gen_random_uuid(), title, snippet_text, source_url, source_type, publisher, tags, DATE '2026-08-14', now()
FROM (VALUES
    (
        'UNESCO guidance on generative AI in education',
        'UNESCO advises education systems to set an age threshold for independent use of generative AI, to validate tools against curriculum and equity goals before adoption, and to keep a human educator accountable for decisions that affect a learner. It stresses that AI should extend teaching capacity rather than replace professional judgement.',
        'https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research',
        'GUIDANCE',
        'UNESCO',
        'education, generative ai, policy, governance, human oversight, learner, teaching, curriculum',
        DATE '2026-08-14'
    ),
    (
        'EU AI Act classifies education and assessment uses as high risk',
        'Regulation (EU) 2024/1689 places AI used to determine access to education, to evaluate learning outcomes, and to monitor and detect prohibited behaviour during tests in its high-risk category. High-risk systems carry obligations for risk management, data governance, logging, transparency to users, human oversight and accuracy testing.',
        'https://eur-lex.europa.eu/eli/reg/2024/1689/oj',
        'LAW',
        'European Union',
        'regulation, compliance, high risk, assessment, exam, proctoring, monitoring, admission, evaluation, audit, logging',
        DATE '2026-08-14'
    ),
    (
        'NIST AI Risk Management Framework',
        'The NIST AI RMF organises trustworthy AI practice into four functions: govern, map, measure and manage. It treats validity and reliability, safety, security, accountability, explainability, privacy and fairness as properties that must be measured continuously rather than certified once, and recommends documenting known limitations alongside intended use.',
        'https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf',
        'STANDARD',
        'National Institute of Standards and Technology',
        'risk management, governance, trustworthy ai, measurement, fairness, explainability, accountability, validity, reliability, monitoring',
        DATE '2026-08-14'
    ),
    (
        'OECD AI Principles on transparency and contestability',
        'The OECD AI Principles ask that people affected by an AI-assisted outcome be told they are interacting with AI, be given information they can understand about the factors behind the outcome, and have a practical route to challenge it. Accountability stays with the organisation deploying the system.',
        'https://oecd.ai/en/ai-principles',
        'GUIDANCE',
        'OECD',
        'transparency, contestability, appeal, accountability, explanation, fairness, disclosure',
        DATE '2026-08-14'
    ),
    (
        'GDPR Article 22 on automated decisions about individuals',
        'GDPR Article 22 gives individuals the right not to be subject to a decision based solely on automated processing where it produces legal or similarly significant effects. Where such processing is permitted, the controller must provide safeguards including the right to obtain human intervention, to express a point of view, and to contest the decision.',
        'https://gdpr-info.eu/art-22-gdpr/',
        'LAW',
        'EU General Data Protection Regulation',
        'automated decision, human intervention, appeal, grading, scoring, result, privacy, safeguards, personal data',
        DATE '2026-08-14'
    ),
    (
        'India DPDP Act framework for personal data',
        'India''s Digital Personal Data Protection framework, published by MeitY, requires notice and consent for processing personal data, purpose limitation, retention only for as long as the purpose requires, and reasonable security safeguards, with additional care for the data of children.',
        'https://www.meity.gov.in/data-protection-framework',
        'LAW',
        'Ministry of Electronics and Information Technology, India',
        'privacy, personal data, consent, retention, candidate data, biometric, security, india, minors',
        DATE '2026-08-14'
    ),
    (
        'NIST evaluation of demographic differences in face recognition',
        'NIST''s Face Recognition Vendor Test programme measures accuracy across demographic groups and has repeatedly found that false match and false non-match rates differ by age, sex and country of origin, with the size of the gap varying widely between algorithms. Systems that verify identity therefore need per-group error measurement, not a single headline accuracy figure.',
        'https://pages.nist.gov/frvt/html/frvt11.html',
        'RESEARCH',
        'National Institute of Standards and Technology',
        'face recognition, identity verification, bias, demographic, false match, proctoring, candidate, accuracy, error rate',
        DATE '2026-08-14'
    ),
    (
        '1EdTech QTI standard for portable assessment content',
        'The Question and Test Interoperability specification defines a common format for questions, tests and results so that items can move between authoring tools, banks and delivery platforms without re-keying. Adopting it reduces lock-in and makes automated content pipelines feasible across systems.',
        'https://www.1edtech.org/standards/qti',
        'STANDARD',
        '1EdTech Consortium',
        'question bank, item, interoperability, authoring, content, metadata, tagging, migration, standard, format',
        DATE '2026-08-14'
    ),
    (
        'WCAG 2.2 accessibility requirements for digital content',
        'The Web Content Accessibility Guidelines set testable success criteria for perceivable, operable, understandable and robust digital content, including text alternatives, keyboard operation, sufficient contrast and predictable behaviour. Any generated or reformatted assessment content inherits these obligations.',
        'https://www.w3.org/TR/WCAG22/',
        'STANDARD',
        'World Wide Web Consortium',
        'accessibility, wcag, candidate experience, inclusion, content, generated content, compliance, usability',
        DATE '2026-08-14'
    ),
    (
        'US Department of Education on keeping humans in the loop',
        'The US Department of Education''s report on AI in teaching and learning argues for a "humans in the loop" default: AI should surface patterns and drafts, while educators retain the decision. It warns that automated systems inherit the biases of their training data and that the burden of proof for fairness lies with the deploying institution.',
        'https://www.ed.gov/sites/ed/files/documents/ai-report/ai-report.pdf',
        'GUIDANCE',
        'US Department of Education, Office of Educational Technology',
        'human in the loop, teaching, learning, bias, oversight, educator, decision, review, grading, feedback',
        DATE '2026-08-14'
    ),
    (
        'Stanford AI Index on adoption and cost trends',
        'The Stanford HAI AI Index tracks steep falls in the cost of inference for a given level of capability, alongside rapid growth in organisational adoption. It also documents that benchmark performance improves faster than evaluation practice does, so organisations increasingly rely on task-specific evaluation rather than published benchmarks.',
        'https://aiindex.stanford.edu/report/',
        'RESEARCH',
        'Stanford Institute for Human-Centered AI',
        'adoption, cost, inference, benchmark, evaluation, trend, capability, industry',
        DATE '2026-08-14'
    ),
    (
        'Survey of large language models applied to education',
        'A 2024 survey of large language models in education catalogues their use for question generation, automated grading, feedback and tutoring, and identifies the recurring obstacles: hallucinated content, sensitivity to prompt wording, difficulty evaluating open-ended output at scale, and the privacy implications of sending learner work to third-party models.',
        'https://arxiv.org/abs/2403.18105',
        'RESEARCH',
        'arXiv (Large Language Models for Education: A Survey and Outlook)',
        'question generation, item generation, grading, feedback, tutoring, hallucination, prompt, evaluation, privacy, learner, doubt, support',
        DATE '2026-08-14'
    ),
    (
        'ETS research programme on automated scoring',
        'ETS publishes research on automated scoring of constructed responses, including how engines are trained on human-rated samples, how agreement with human raters is measured, and why automated scores are commonly combined with human scoring rather than used alone for high-stakes decisions.',
        'https://www.ets.org/research.html',
        'VENDOR',
        'Educational Testing Service',
        'automated scoring, essay, constructed response, rater, agreement, moderation, grading, evaluation, high stakes, descriptive',
        DATE '2026-08-14'
    ),
    (
        'Duolingo English Test research on computer-adaptive testing',
        'The Duolingo English Test research programme documents an operational pipeline that uses automatic item generation to keep a large item pool fresh, computer-adaptive delivery to shorten tests, and a mix of automated scoring and human review, with remote proctoring recordings reviewed after the session rather than live.',
        'https://englishtest.duolingo.com/research',
        'VENDOR',
        'Duolingo English Test',
        'item generation, item pool, adaptive, remote proctoring, asynchronous review, scoring, security, exposure',
        DATE '2026-08-14'
    ),
    (
        'Turnitin on the limits of AI writing detection',
        'Turnitin''s material on AI writing detection states that a detection score is an indicator requiring human interpretation rather than proof of misconduct, that false positives occur particularly on short passages and non-native writing, and that institutions should treat a flag as the start of a conversation with the student.',
        'https://www.turnitin.com/solutions/ai-writing',
        'VENDOR',
        'Turnitin',
        'detection, integrity, false positive, misconduct, flag, review, writing, plagiarism, evidence',
        DATE '2026-08-14'
    ),
    (
        'Automated essay scoring: background and criticisms',
        'General reference material on automated essay scoring describes how systems are trained to predict human ratings from surface and linguistic features, and summarises the standing criticisms: susceptibility to gaming through length and vocabulary, weak handling of unusual but valid arguments, and the need to validate each new prompt separately.',
        'https://en.wikipedia.org/wiki/Automated_essay_scoring',
        'GENERAL_WEB',
        'Wikipedia',
        'essay, scoring, rubric, gaming, validity, prompt, descriptive answer, marking, moderation',
        DATE '2026-08-14'
    )
) AS seed(title, snippet_text, source_url, source_type, publisher, tags, retrieved_at);

-- ---------------------------------------------------------------------
-- Sample processes (current state only)
-- ---------------------------------------------------------------------
INSERT INTO process (id, name, industry, description, status, origin, created_at, updated_at)
SELECT gen_random_uuid(), name, 'Online Education & Digital Assessment', description,
       'CURRENT_ONLY', 'SEED', now(), now()
FROM (VALUES
    (
        'Online Assessment Creation',
        'How AssessWise turns a client''s assessment requirement into a live, scheduled online exam: gathering the requirement, designing a blueprint, authoring and reviewing questions, configuring delivery and proctoring rules, and publishing.'
    ),
    (
        'Question Bank Management',
        'How AssessWise keeps its shared question bank healthy: ingesting items from subject matter experts, tagging them for reuse, removing duplicates, reviewing live item statistics, and retiring or revising items that perform badly.'
    ),
    (
        'Candidate Onboarding & Proctoring',
        'How AssessWise gets a candidate from registration to a completed, credible exam session: eligibility checks, system readiness, identity verification at exam start, live monitoring for integrity violations, and adjudication of flagged incidents.'
    ),
    (
        'Result Evaluation & Grading',
        'How AssessWise converts submitted attempts into published results: automatic scoring of objective questions, allocation and manual grading of descriptive answers against a rubric, moderation across evaluators, re-evaluation requests, and result publication.'
    ),
    (
        'Certification Issuance',
        'How AssessWise issues a certificate once a candidate passes: validating eligibility against the passing criteria, generating the certificate, obtaining approval and signature, distributing it, and answering verification requests from employers.'
    ),
    (
        'Learner Support & Doubt Resolution',
        'How AssessWise handles the questions candidates and institutions raise before, during and after an exam: intake across channels, classification and prioritisation, resolution from the knowledge base, escalation of technical and academic issues, and closure with feedback.'
    )
) AS seed(name, description);

-- ---------------------------------------------------------------------
-- Current-state activities
-- ---------------------------------------------------------------------
INSERT INTO activity (id, process_id, name, sequence_order, description, created_at)
SELECT gen_random_uuid(), p.id, seed.name, seed.sequence_order, seed.description, now()
FROM (VALUES
    -- Online Assessment Creation
    ('Online Assessment Creation', 1, 'Capture assessment requirements from the client',
     'The exam coordinator collects the syllabus, duration, question mix, difficulty expectations and delivery window from the client, mostly over email and calls.'),
    ('Online Assessment Creation', 2, 'Design the assessment blueprint',
     'A subject matter expert and an assessment designer agree the topic weightings, difficulty distribution and marking scheme in a spreadsheet.'),
    ('Online Assessment Creation', 3, 'Author questions in the platform',
     'Content authors write new questions or adapt existing ones, entering stems, options, correct answers, marks and metadata by hand.'),
    ('Online Assessment Creation', 4, 'Internal quality review of the paper',
     'A QA analyst checks each question for factual accuracy, ambiguity, language quality, duplicate coverage and alignment to the blueprint, and returns comments to the author.'),
    ('Online Assessment Creation', 5, 'Configure delivery and proctoring rules',
     'The coordinator sets the exam window, navigation and shuffling rules, permitted resources, and the proctoring strictness level for the assessment.'),
    ('Online Assessment Creation', 6, 'Publish and schedule the assessment',
     'The finalised paper is published to the assessment platform, linked to the candidate batch, and scheduled for the agreed window.'),

    -- Question Bank Management
    ('Question Bank Management', 1, 'Ingest questions from subject matter experts',
     'Questions arrive as documents and spreadsheets in inconsistent formats and are copied into the question bank by content authors.'),
    ('Question Bank Management', 2, 'Tag questions with topic, difficulty and cognitive level',
     'Each item is manually labelled with subject, topic, sub-topic, expected difficulty and Bloom level so that blueprints can be filled later.'),
    ('Question Bank Management', 3, 'Check for duplicate and near-duplicate items',
     'A QA analyst searches for items that repeat existing content, relying on keyword search and memory of the bank.'),
    ('Question Bank Management', 4, 'Review live item statistics',
     'A psychometrician reviews difficulty and discrimination indices from delivered exams to find items that are too easy, too hard or not discriminating.'),
    ('Question Bank Management', 5, 'Retire or revise weak items',
     'Items that fail review are edited by the original author or retired, and the change is recorded against the item history.'),

    -- Candidate Onboarding & Proctoring
    ('Candidate Onboarding & Proctoring', 1, 'Register candidates and verify eligibility',
     'Registration officers check candidate details and supporting documents against the eligibility rules for the exam and approve or reject the registration.'),
    ('Candidate Onboarding & Proctoring', 2, 'Run system and environment readiness checks',
     'Candidates run a pre-exam check for camera, microphone, bandwidth and browser, and support executives handle the failures that come in as tickets.'),
    ('Candidate Onboarding & Proctoring', 3, 'Verify candidate identity at exam start',
     'The candidate photographs an identity document and their face; a proctor compares the two and admits or holds the candidate.'),
    ('Candidate Onboarding & Proctoring', 4, 'Monitor live sessions for integrity violations',
     'Proctors watch video tiles for multiple faces, absence, phone use, suspicious eye movement and background voices, raising a flag when they see something.'),
    ('Candidate Onboarding & Proctoring', 5, 'Adjudicate flagged incidents',
     'An integrity review panel replays the recording around each flag, decides whether a violation occurred, and records the outcome and any sanction.'),

    -- Result Evaluation & Grading
    ('Result Evaluation & Grading', 1, 'Auto-score objective questions',
     'The platform scores multiple-choice and other objective items automatically against the stored answer key.'),
    ('Result Evaluation & Grading', 2, 'Allocate descriptive answers to evaluators',
     'The evaluation coordinator distributes descriptive answers across available evaluators, balancing load and avoiding conflicts of interest.'),
    ('Result Evaluation & Grading', 3, 'Grade descriptive answers against the rubric',
     'Evaluators read each answer and award marks per rubric criterion, adding a short justification comment where marks are withheld.'),
    ('Result Evaluation & Grading', 4, 'Moderate and normalise scores',
     'A moderator samples graded scripts, compares evaluators against each other, and applies normalisation where an evaluator is consistently strict or lenient.'),
    ('Result Evaluation & Grading', 5, 'Handle re-evaluation requests',
     'Candidates who dispute a result request re-evaluation; the coordinator assigns a second evaluator and compares the two outcomes.'),
    ('Result Evaluation & Grading', 6, 'Publish results',
     'Final scores, grades and rank data are released to candidates and to the client institution.'),

    -- Certification Issuance
    ('Certification Issuance', 1, 'Validate eligibility against passing criteria',
     'The certification officer checks the published result, attendance and any programme-specific conditions before a certificate can be raised.'),
    ('Certification Issuance', 2, 'Generate the certificate',
     'Candidate name, programme, score and date are entered into a certificate template and rendered as a PDF.'),
    ('Certification Issuance', 3, 'Obtain approval and digital signature',
     'The programme head reviews the batch and applies the digital signature, often in a single weekly sitting.'),
    ('Certification Issuance', 4, 'Issue and distribute certificates',
     'Certificates are emailed to candidates and posted to the candidate portal, and the issuance is logged.'),
    ('Certification Issuance', 5, 'Answer employer verification requests',
     'Support executives look up a certificate number on request and confirm authenticity by email.'),

    -- Learner Support & Doubt Resolution
    ('Learner Support & Doubt Resolution', 1, 'Receive queries across channels',
     'Queries arrive by email, in-portal chat and phone, and are logged as tickets, sometimes twice when a candidate uses more than one channel.'),
    ('Learner Support & Doubt Resolution', 2, 'Classify and prioritise tickets',
     'A support executive reads each ticket, assigns a category and priority, and routes it to the right queue.'),
    ('Learner Support & Doubt Resolution', 3, 'Resolve common queries from the knowledge base',
     'Executives search the knowledge base for an existing answer and paste an adapted version into the reply.'),
    ('Learner Support & Doubt Resolution', 4, 'Escalate technical and academic issues',
     'Tickets that cannot be answered from the knowledge base are escalated to the support lead, the platform team or the academic team.'),
    ('Learner Support & Doubt Resolution', 5, 'Close the ticket and capture feedback',
     'The executive confirms resolution with the candidate, closes the ticket and records a resolution code and satisfaction rating.')
) AS seed(process_name, sequence_order, name, description)
JOIN process p ON p.name = seed.process_name;

-- ---------------------------------------------------------------------
-- Which roles perform which activities
-- ---------------------------------------------------------------------
INSERT INTO activity_role (activity_id, role_id)
SELECT a.id, r.id
FROM (VALUES
    ('Online Assessment Creation', 'Capture assessment requirements from the client', 'Exam Coordinator'),
    ('Online Assessment Creation', 'Design the assessment blueprint', 'Assessment Designer'),
    ('Online Assessment Creation', 'Design the assessment blueprint', 'Subject Matter Expert'),
    ('Online Assessment Creation', 'Author questions in the platform', 'Content Author'),
    ('Online Assessment Creation', 'Author questions in the platform', 'Subject Matter Expert'),
    ('Online Assessment Creation', 'Internal quality review of the paper', 'QA Analyst'),
    ('Online Assessment Creation', 'Configure delivery and proctoring rules', 'Exam Coordinator'),
    ('Online Assessment Creation', 'Publish and schedule the assessment', 'Exam Coordinator'),

    ('Question Bank Management', 'Ingest questions from subject matter experts', 'Content Author'),
    ('Question Bank Management', 'Ingest questions from subject matter experts', 'Subject Matter Expert'),
    ('Question Bank Management', 'Tag questions with topic, difficulty and cognitive level', 'Assessment Designer'),
    ('Question Bank Management', 'Check for duplicate and near-duplicate items', 'QA Analyst'),
    ('Question Bank Management', 'Review live item statistics', 'Psychometrician'),
    ('Question Bank Management', 'Retire or revise weak items', 'Assessment Designer'),
    ('Question Bank Management', 'Retire or revise weak items', 'Content Author'),

    ('Candidate Onboarding & Proctoring', 'Register candidates and verify eligibility', 'Registration Officer'),
    ('Candidate Onboarding & Proctoring', 'Run system and environment readiness checks', 'Support Executive'),
    ('Candidate Onboarding & Proctoring', 'Verify candidate identity at exam start', 'Proctor'),
    ('Candidate Onboarding & Proctoring', 'Monitor live sessions for integrity violations', 'Proctor'),
    ('Candidate Onboarding & Proctoring', 'Adjudicate flagged incidents', 'Integrity Review Panel'),

    ('Result Evaluation & Grading', 'Auto-score objective questions', 'Evaluation Coordinator'),
    ('Result Evaluation & Grading', 'Allocate descriptive answers to evaluators', 'Evaluation Coordinator'),
    ('Result Evaluation & Grading', 'Grade descriptive answers against the rubric', 'Evaluator'),
    ('Result Evaluation & Grading', 'Moderate and normalise scores', 'Moderator'),
    ('Result Evaluation & Grading', 'Handle re-evaluation requests', 'Evaluation Coordinator'),
    ('Result Evaluation & Grading', 'Handle re-evaluation requests', 'Evaluator'),
    ('Result Evaluation & Grading', 'Publish results', 'Exam Coordinator'),

    ('Certification Issuance', 'Validate eligibility against passing criteria', 'Certification Officer'),
    ('Certification Issuance', 'Generate the certificate', 'Certification Officer'),
    ('Certification Issuance', 'Obtain approval and digital signature', 'Programme Head'),
    ('Certification Issuance', 'Issue and distribute certificates', 'Certification Officer'),
    ('Certification Issuance', 'Answer employer verification requests', 'Support Executive'),

    ('Learner Support & Doubt Resolution', 'Receive queries across channels', 'Support Executive'),
    ('Learner Support & Doubt Resolution', 'Classify and prioritise tickets', 'Support Executive'),
    ('Learner Support & Doubt Resolution', 'Resolve common queries from the knowledge base', 'Support Executive'),
    ('Learner Support & Doubt Resolution', 'Escalate technical and academic issues', 'Support Lead'),
    ('Learner Support & Doubt Resolution', 'Close the ticket and capture feedback', 'Support Executive')
) AS seed(process_name, activity_name, role_name)
JOIN process p ON p.name = seed.process_name
JOIN activity a ON a.process_id = p.id AND a.name = seed.activity_name
JOIN role r ON r.name = seed.role_name;

-- ---------------------------------------------------------------------
-- Which systems support which activities
-- ---------------------------------------------------------------------
INSERT INTO activity_system (activity_id, system_id)
SELECT a.id, s.id
FROM (VALUES
    ('Online Assessment Creation', 'Capture assessment requirements from the client', 'Email'),
    ('Online Assessment Creation', 'Capture assessment requirements from the client', 'Ticketing System'),
    ('Online Assessment Creation', 'Design the assessment blueprint', 'Spreadsheet'),
    ('Online Assessment Creation', 'Design the assessment blueprint', 'Question Bank'),
    ('Online Assessment Creation', 'Author questions in the platform', 'Authoring Tool'),
    ('Online Assessment Creation', 'Author questions in the platform', 'Question Bank'),
    ('Online Assessment Creation', 'Internal quality review of the paper', 'Authoring Tool'),
    ('Online Assessment Creation', 'Configure delivery and proctoring rules', 'Assessment Platform'),
    ('Online Assessment Creation', 'Configure delivery and proctoring rules', 'Proctoring Engine'),
    ('Online Assessment Creation', 'Publish and schedule the assessment', 'Assessment Platform'),

    ('Question Bank Management', 'Ingest questions from subject matter experts', 'Spreadsheet'),
    ('Question Bank Management', 'Ingest questions from subject matter experts', 'Question Bank'),
    ('Question Bank Management', 'Tag questions with topic, difficulty and cognitive level', 'Question Bank'),
    ('Question Bank Management', 'Check for duplicate and near-duplicate items', 'Question Bank'),
    ('Question Bank Management', 'Review live item statistics', 'Analytics Dashboard'),
    ('Question Bank Management', 'Retire or revise weak items', 'Question Bank'),
    ('Question Bank Management', 'Retire or revise weak items', 'Authoring Tool'),

    ('Candidate Onboarding & Proctoring', 'Register candidates and verify eligibility', 'Candidate Portal'),
    ('Candidate Onboarding & Proctoring', 'Run system and environment readiness checks', 'Candidate Portal'),
    ('Candidate Onboarding & Proctoring', 'Run system and environment readiness checks', 'Proctoring Engine'),
    ('Candidate Onboarding & Proctoring', 'Verify candidate identity at exam start', 'Proctoring Engine'),
    ('Candidate Onboarding & Proctoring', 'Monitor live sessions for integrity violations', 'Proctoring Engine'),
    ('Candidate Onboarding & Proctoring', 'Adjudicate flagged incidents', 'Case Management System'),
    ('Candidate Onboarding & Proctoring', 'Adjudicate flagged incidents', 'Proctoring Engine'),

    ('Result Evaluation & Grading', 'Auto-score objective questions', 'Assessment Platform'),
    ('Result Evaluation & Grading', 'Allocate descriptive answers to evaluators', 'Evaluation Console'),
    ('Result Evaluation & Grading', 'Grade descriptive answers against the rubric', 'Evaluation Console'),
    ('Result Evaluation & Grading', 'Moderate and normalise scores', 'Analytics Dashboard'),
    ('Result Evaluation & Grading', 'Handle re-evaluation requests', 'Case Management System'),
    ('Result Evaluation & Grading', 'Publish results', 'Assessment Platform'),

    ('Certification Issuance', 'Validate eligibility against passing criteria', 'Assessment Platform'),
    ('Certification Issuance', 'Generate the certificate', 'Certificate Generator'),
    ('Certification Issuance', 'Obtain approval and digital signature', 'Certificate Generator'),
    ('Certification Issuance', 'Issue and distribute certificates', 'Email'),
    ('Certification Issuance', 'Issue and distribute certificates', 'Candidate Portal'),
    ('Certification Issuance', 'Answer employer verification requests', 'Email'),

    ('Learner Support & Doubt Resolution', 'Receive queries across channels', 'Ticketing System'),
    ('Learner Support & Doubt Resolution', 'Receive queries across channels', 'Email'),
    ('Learner Support & Doubt Resolution', 'Classify and prioritise tickets', 'Ticketing System'),
    ('Learner Support & Doubt Resolution', 'Resolve common queries from the knowledge base', 'Knowledge Base'),
    ('Learner Support & Doubt Resolution', 'Escalate technical and academic issues', 'Ticketing System'),
    ('Learner Support & Doubt Resolution', 'Close the ticket and capture feedback', 'Ticketing System')
) AS seed(process_name, activity_name, system_name)
JOIN process p ON p.name = seed.process_name
JOIN activity a ON a.process_id = p.id AND a.name = seed.activity_name
JOIN system_tool s ON s.name = seed.system_name;

-- ---------------------------------------------------------------------
-- Pain points already known to the business.
--
-- Source SEED, not AI_GENERATED: these are input to the pipeline and survive a re-analysis.
-- Figures are illustrative of a mid-size assessment operation, not measurements of a real client.
-- ---------------------------------------------------------------------
INSERT INTO problem (id, process_id, activity_id, description, severity, source, created_at)
SELECT gen_random_uuid(), p.id, a.id, seed.description, seed.severity, 'SEED', now()
FROM (VALUES
    ('Online Assessment Creation', 'Design the assessment blueprint',
     'Blueprint sign-off takes three to five working days because reviewer feedback arrives as unstructured email threads.', 'HIGH'),
    ('Online Assessment Creation', 'Author questions in the platform',
     'Expected difficulty is set from the author''s judgement alone, with no reference to how similar items have performed.', 'MEDIUM'),
    ('Online Assessment Creation', 'Internal quality review of the paper',
     'Quality review is a full manual read of every item, so review effort scales linearly with paper length.', 'HIGH'),

    ('Question Bank Management', 'Check for duplicate and near-duplicate items',
     'Near-duplicate items are usually discovered only when a candidate reports seeing the same question twice.', 'HIGH'),
    ('Question Bank Management', 'Tag questions with topic, difficulty and cognitive level',
     'Tagging conventions differ between authors, so blueprint queries return incomplete item sets.', 'MEDIUM'),
    ('Question Bank Management', 'Review live item statistics',
     'Item statistics are reviewed in a quarterly batch, so a badly performing item can stay live for a full cycle.', 'MEDIUM'),

    ('Candidate Onboarding & Proctoring', 'Monitor live sessions for integrity violations',
     'A single proctor monitors up to thirty concurrent candidates, so genuine violations are missed at peak load.', 'HIGH'),
    ('Candidate Onboarding & Proctoring', 'Adjudicate flagged incidents',
     'Adjudication means scrubbing through session video by hand, averaging around twelve minutes per flagged session.', 'HIGH'),
    ('Candidate Onboarding & Proctoring', 'Run system and environment readiness checks',
     'Readiness failures surface as support tickets minutes before the exam window, when there is no time to fix them.', 'MEDIUM'),

    ('Result Evaluation & Grading', 'Grade descriptive answers against the rubric',
     'Descriptive grading for a large cohort takes six to nine days and is the main driver of delayed results.', 'HIGH'),
    ('Result Evaluation & Grading', 'Moderate and normalise scores',
     'Two evaluators can differ by a wide margin on the same answer, which drives avoidable re-evaluation requests.', 'HIGH'),
    ('Result Evaluation & Grading', 'Handle re-evaluation requests',
     'Every re-evaluation is a full second grading pass, with no way to triage which requests are likely to succeed.', 'MEDIUM'),

    ('Certification Issuance', 'Answer employer verification requests',
     'Employer verification is answered by hand and takes two to three working days.', 'MEDIUM'),
    ('Certification Issuance', 'Generate the certificate',
     'Certificate fields are re-keyed from the result sheet, which introduces name and date errors that are found after issuance.', 'MEDIUM'),
    ('Certification Issuance', 'Obtain approval and digital signature',
     'Signing happens in a weekly batch, so a candidate who passes on a Monday may wait a week for the certificate.', 'LOW'),

    ('Learner Support & Doubt Resolution', 'Resolve common queries from the knowledge base',
     'Around three in five tickets are repeat questions that are already answered in the knowledge base.', 'HIGH'),
    ('Learner Support & Doubt Resolution', 'Receive queries across channels',
     'The same candidate query arrives on two channels and is worked twice by different executives.', 'MEDIUM'),
    ('Learner Support & Doubt Resolution', 'Classify and prioritise tickets',
     'First-response time exceeds the four-hour target during exam windows, when ticket volume roughly triples.', 'HIGH')
) AS seed(process_name, activity_name, description, severity)
JOIN process p ON p.name = seed.process_name
JOIN activity a ON a.process_id = p.id AND a.name = seed.activity_name;
