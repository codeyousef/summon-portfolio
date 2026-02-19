---
title: "How to Use This Curriculum"
section_id: ""
phase: 0
phase_title: "Appendix"
order: 4
---

# How to Use This Curriculum

You have found this curriculum, read the overview, maybe skimmed a few lessons, and now you are wondering: what is the best way to actually work through this thing? This appendix covers prerequisites, pacing, study habits, environment setup, and the practical logistics of turning 14 phases of material into genuine competence. Spending 20 minutes here will save you days of false starts.

---

## Prerequisites: What You Need Before Lesson 1.1

The overview lists prerequisites briefly. Here they are in more detail, with honest assessments of what "enough" looks like.

### Python Proficiency

You should be able to:

- Write classes with `__init__`, instance methods, and inheritance
- Use list comprehensions, dictionary comprehensions, and generators
- Work with files, handle exceptions, and use context managers (`with` statements)
- Navigate the standard library: `os`, `math`, `collections`, `itertools`
- Debug using print statements, assertions, and basic use of `pdb`
- Understand how imports and packages work

You do **not** need to be a Python expert. You do not need to know metaclasses, decorators with arguments, or async/await. If you can write a 200-line script that reads a CSV, processes the data, and writes results to a file without constantly looking up syntax, you are ready.

If you are not there yet, spend one to two weeks on Python fundamentals. Work through practical exercises, not abstract tutorials. Build a small project -- a command-line tool, a data processor, a simple game. The goal is fluency, not mastery.

### Linear Algebra

The core operations you need to understand intuitively:

- **Vectors**: addition, scalar multiplication, dot product, norms
- **Matrices**: multiplication (and the shape rules that govern it), transpose, element-wise operations
- **The expression Wx + b**: you should immediately recognize this as a linear transformation (matrix-vector multiply) with a bias offset, and know the resulting shape
- **Broadcasting**: how operations work when shapes do not match exactly (NumPy will teach you this quickly)

You do **not** need eigenvalues, SVD, or matrix calculus at the start. These become useful in later phases (SVD matters for LoRA, eigenvalues for understanding optimization landscapes), but you will build up to them gradually.

If `Wx + b` does not immediately make sense, watch 3Blue1Brown's "Essence of Linear Algebra" series on YouTube. It is roughly 3 hours and will give you the geometric intuition that makes everything in this curriculum more concrete.

### Calculus

You need:

- **Derivatives as rates of change**: if f(x) = x^2, then f'(x) = 2x, and this tells you how fast f changes as x changes
- **The chain rule**: if y = f(g(x)), then dy/dx = f'(g(x)) * g'(x). This is the mathematical backbone of backpropagation, and you will implement it in Phase 1
- **Partial derivatives**: the idea that for a function of multiple variables, you can take the derivative with respect to one variable while holding the others fixed

You do **not** need integration, differential equations, or multivariable calculus beyond partial derivatives. You do not need to be able to compute derivatives by hand quickly -- the autograd engine will do that. You need to understand what a derivative means and how the chain rule composes them.

### Probability and Statistics

The minimum:

- **Probability distributions**: what a normal (Gaussian) distribution is, what mean and variance represent
- **Conditional probability**: P(A|B), the idea that observing one thing changes the probability of another
- **Expected value**: the average outcome of a random variable
- **Sampling**: drawing values from a distribution

You will encounter more sophisticated probability in later phases (KL divergence, evidence lower bounds, Bayesian reasoning), but these are introduced when they are needed. The basics above are sufficient to start.

---

## Setting Up Your Development Environment

Get this done before you start Lesson 1.1. A working environment removes friction and lets you focus on learning.

### Python Installation

Install **Python 3.10 or later**. If you are on macOS or Linux, your system may already have Python, but it is often an older version. Use `python3 --version` to check.

For managing Python versions, `pyenv` (macOS/Linux) or the official Python installer (Windows) work well. Avoid Anaconda unless you are already comfortable with it -- it adds complexity that is unnecessary for this curriculum.

### Virtual Environment

Create a dedicated virtual environment for the curriculum. This keeps your dependencies isolated:

- Using `venv`: create and activate a virtual environment in your project directory
- Using `conda`: create a named environment with your target Python version
- Using `uv`: a faster alternative to pip that handles virtual environments as well

Pick whichever tool you already know. If you have no preference, `venv` is the simplest and requires no additional installation.

### PyTorch

Install PyTorch with CUDA support if you have an NVIDIA GPU. Visit the PyTorch website and use their installation selector to get the right command for your platform and CUDA version.

If you do not have a GPU, install the CPU-only version. Phases 1 through 3 work fine on CPU, and you will move to GPU access (via cloud or hardware purchase) by Phase 4 when you actually need it.

After installation, verify it works: import torch, check the version, and if you installed CUDA support, verify that `torch.cuda.is_available()` returns True.

### Jupyter

Install JupyterLab or Jupyter Notebook. You will use Jupyter for interactive experimentation, especially in the early phases where you want to inspect intermediate values, visualize data, and iterate quickly.

Some lessons include interactive REPL blocks (more on those below). These are designed to be run in Jupyter cells where you can execute code in small increments and see results immediately.

That said, you should also be comfortable writing standalone Python scripts. Many build-alongs are better organized as `.py` files, especially once your implementations grow beyond a few hundred lines. Use Jupyter for exploration and scripts for production code.

### Essential Libraries

Install NumPy, Matplotlib, and tqdm. These are used from the very first lesson:

- **NumPy** for array operations and your initial from-scratch implementations
- **Matplotlib** for plotting loss curves, visualizing data, and rendering attention heatmaps
- **tqdm** for progress bars during training loops

Additional libraries are introduced in later phases. Each lesson lists its specific dependencies. Do not install everything at once -- install as you go.

### GPU Access Options

If you do not own an NVIDIA GPU, here are your options in order of cost:

- **Google Colab (free tier)**: Tesla T4 with 16 GB VRAM. Session time limits, but sufficient for Phases 1 through 3.
- **Kaggle Notebooks (free)**: Similar to Colab, with 30 hours of GPU per week.
- **Google Colab Pro ($10/month)**: Longer sessions, priority GPU access. Worth it for consistent study.
- **RunPod / Vast.ai / Lambda Labs**: On-demand cloud GPUs, $0.70 to $1.50 per hour for an A100. Use for Phase 4 onward.
- **Buy a consumer GPU**: An RTX 4090 (24 GB VRAM) at roughly $1,600 USD handles everything through Phase 10. If you plan to study ML for more than six months, buying is usually cheaper than renting.

See the **Recommended Hardware Progression** appendix for detailed guidance on what hardware you need at each phase and how to manage cloud costs.

### Editor or IDE

Use whatever you are comfortable with. VS Code, PyCharm, vim -- the editor does not matter. What matters is that you can navigate code, run scripts, and use a debugger. If you have no preference, VS Code with the Python and Jupyter extensions is a solid default.

---

## How to Approach Each Lesson

Every lesson in this curriculum follows a consistent structure. Here is how to work through each one effectively.

### Step 1: Read the Core Concepts (30-60 Minutes)

Read the conceptual material once through, at a natural pace. Do not try to memorize anything. Do not take extensive notes. The goal is to get the shape of the ideas in your head -- what problem is being solved, what approach is being taken, and roughly how the pieces fit together.

If something does not make sense on the first read, that is normal and expected. Many concepts only click when you see them in code. Mark confusing sections mentally and move on. You will return to them.

### Step 2: Start the Build-Along Immediately

Open your editor. Create a new file. Start writing code. This is the core of the curriculum, and it should consume the majority of your time on each lesson.

The build-alongs are structured to be self-contained. They tell you what to build, provide enough detail to get started, and include checkpoints to verify your implementation. They do not give you the complete code -- that is intentional.

As you build, refer back to the Core Concepts section freely. You will find that the concepts make much more sense now that you have concrete questions: "Wait, does the softmax go before or after the scaling? Let me re-read that section."

### Step 3: Hit the Checkpoints

Checkpoints are non-negotiable. They are concrete, measurable criteria like "your gradients should match PyTorch to 6 decimal places" or "your model should achieve 85% accuracy on the test set." Do not move on until you have met them.

If your implementation does not meet a checkpoint, debug it. Print intermediate values. Compare shapes. Step through the computation with a small example you can verify by hand. This debugging process is not a distraction from the curriculum -- it is the curriculum. The skills you develop here (diagnosing gradient flow problems, spotting shape mismatches, reasoning about numerical precision) are exactly the skills that separate effective practitioners from people who can only follow tutorials.

### Step 4: Do the Guided Exercises

If the lesson includes guided exercises, do them. They are not busywork. They typically involve ablation studies (what happens when you change this hyperparameter?), debugging challenges (here is a broken training loop, find the bug), or exploration tasks (visualize how attention patterns change during training). These exercises build the intuition that makes later phases feel navigable rather than overwhelming.

### Step 5: Reflect Briefly

After completing a lesson, spend five minutes reflecting. What was the core idea? What surprised you? What was harder than expected? What would you explain differently to someone else?

You do not need to write a formal summary (though some people find it helpful). Even a few sentences in a text file or notebook is valuable. This reflection step aids consolidation and gives you a record to look back on when you revisit material later.

---

## Using the Interactive REPL Blocks

Several lessons in the early phases include interactive REPL blocks -- small, focused code snippets designed to be run one cell at a time in a Jupyter notebook. These are marked clearly in the lesson text.

The purpose of REPL blocks is to let you build understanding incrementally. Instead of writing a complete implementation and hoping it works, you write one piece, inspect the output, verify it makes sense, and then move on to the next piece.

Here is how to use them effectively:

- **Run each block individually**, not all at once. The point is to see intermediate results.
- **Inspect shapes and values** after every operation. If you just computed a matrix multiplication, print the shape of the result. Does it match what you expected? If not, figure out why before continuing.
- **Experiment with variations.** After running a REPL block as written, try changing something. What happens if you double the learning rate? What if you use a different activation function? What if you feed in a different batch size? These small experiments build intuition faster than passive reading.
- **Transition to scripts when ready.** REPL blocks are scaffolding. Once you understand the concept, incorporate it into a proper Python script. The build-alongs assume you are working in scripts, not notebooks, for anything beyond quick experiments.

By Phase 3, you will rarely need REPL blocks. You will have enough experience to write implementations directly and debug them effectively. The REPL blocks exist to build that confidence in the early phases.

---

## Recommended Pace

This curriculum is designed to take roughly 40 weeks at a steady pace. Your actual timeline depends on your available time, prior experience, and learning style.

### Full-Time Schedule (20-30 Hours Per Week)

If you are studying full-time -- perhaps during a career transition, a summer break, or a dedicated learning period -- you can complete the curriculum in approximately 20 to 25 weeks.

A typical day might look like:

- **Morning (3-4 hours)**: Read core concepts for a new lesson, start the build-along
- **Afternoon (2-3 hours)**: Continue building, debug, hit checkpoints
- **Occasional evening (1-2 hours)**: Guided exercises, paper reading, reflection

At this pace, you will move through one lesson every two to three days. Be careful not to rush. Full-time study gives you the luxury of depth -- use it. Spend extra time on build-alongs that interest you. Read the referenced papers. Implement variations.

The risk of full-time study is burnout. Take at least one full day off per week. If you have been staring at gradient computations for six hours and nothing is clicking, go for a walk. The insight will come later, often when you are not trying.

### Part-Time Schedule (8-12 Hours Per Week)

If you are studying alongside a job or other commitments, plan for 40 to 50 weeks. This is the pace the weekly estimates in the overview assume.

A typical week might look like:

- **Two weekday evenings (2 hours each)**: Read core concepts, start build-alongs
- **One weekend block (4-6 hours)**: Focused building time, checkpoints, exercises

Consistency matters more than intensity. Four sessions of two hours will teach you more than one marathon eight-hour session. Your brain needs time between sessions to consolidate what you have learned.

The risk of part-time study is losing momentum. If you miss a week, it takes effort to get back into the material. Combat this by keeping a simple log of where you are and what you were working on. When you sit down after a break, read your last few log entries to reorient yourself.

### Slow and Steady Schedule (4-6 Hours Per Week)

This is entirely viable. The curriculum will take 60 to 80 weeks, which is over a year. That is fine. The material is not going anywhere, and slow study has an advantage: the spacing effect. When you revisit a concept after a week away, the act of recalling it strengthens the memory more than continuous study would.

At this pace, focus on one lesson per week. Read the core concepts in one session, build in the next two or three sessions, and do the exercises in the final session. Keep your development environment set up and ready so you do not waste precious time on logistics.

---

## When to Move On vs. When to Spend More Time

This is one of the most important judgment calls you will make, and there is no formula for it. Here are guidelines.

### Move On When:

- You have met all the checkpoints for the lesson
- You can explain the core idea to someone else without looking at the material
- Your implementation works and you understand why it works (not just that it works)
- You feel a sense of "I get this" even if you could not recite every detail from memory

### Spend More Time When:

- You met the checkpoints but your understanding feels fragile -- you followed the steps but could not reproduce them from scratch
- You have a nagging confusion about something fundamental (not a minor detail, but a core concept like "why does backpropagation actually work" or "what does the attention matrix represent")
- Your implementation works but you copied patterns without understanding them
- You skipped a guided exercise because you assumed you already knew the answer

### Signs You Are Spending Too Much Time:

- You are polishing code style instead of learning new concepts
- You are reading your fifth blog post about the same topic instead of implementing it
- You are trying to achieve perfect understanding before moving on -- perfect understanding comes from seeing the concept used in later phases, not from dwelling on a single lesson
- You are stuck on a bug for more than two hours without making progress. At that point, step away, ask for help (see Community Resources below), or look at a reference implementation

A useful rule of thumb: if you have spent more than 1.5 times the suggested time on a lesson and have met the checkpoints, move on. If you are below 0.5 times the suggested time, you are probably rushing.

---

## Supplementing with Papers

The **Reading List (Papers)** appendix provides a curated set of research papers organized by importance and topic. Here is how to integrate paper reading into your study.

### When to Read Papers

Do not read papers during Phases 1 through 3. You do not have enough context to appreciate them, and they will slow you down without adding much value. Focus on building.

Starting in Phase 4, begin reading the "Must Read" papers from the reading list. Read each paper when you reach the phase it connects to -- the reading list appendix maps each paper to the relevant curriculum phase.

By Phase 7 onward, you should be reading one to two papers per week. This is when paper reading shifts from supplementary to essential. The curriculum covers the most important ideas, but papers provide depth, context, and alternative perspectives that the lessons cannot fully capture.

### How to Read Papers

The reading list appendix includes a detailed section on how to read ML papers effectively. The short version:

1. **First pass (5-10 minutes)**: Abstract, figures, conclusion. Decide if it is worth a deeper read.
2. **Second pass (30-60 minutes)**: Introduction, method, experiments. Focus on intuition over math.
3. **Third pass (implement it)**: The only way to truly understand a paper.

Keep a paper log. For each paper you read, write three to five sentences summarizing the problem, the approach, and the key result. This log becomes invaluable when you need to recall which paper introduced a specific technique.

---

## Building a Portfolio of Implementations

As you work through the curriculum, you are building something valuable: a growing collection of implementations that demonstrate real understanding. Treat this intentionally.

### Organize Your Work

Create a single repository (or a structured directory) for all your curriculum work. Organize it by phase and lesson. Each implementation should have:

- The code itself, reasonably well-commented
- A brief description of what it implements
- Instructions for running it (what command, what data it expects)
- Key results: loss curves, sample outputs, benchmark numbers

You do not need to polish every implementation to production quality. But it should be clean enough that you can return to it six months later and understand what it does.

### What Makes a Strong Portfolio Piece

Not every lesson will produce something portfolio-worthy. The best portfolio pieces are:

- **Complete implementations** of well-known architectures (a transformer from scratch, a diffusion model, LoRA fine-tuning)
- **Comparison studies** where you benchmarked multiple approaches (different attention mechanisms, different sampling strategies, different optimization methods)
- **Reproductions** of results from papers, with your own analysis of what worked and what did not
- **The capstone project** from Phase 14, which synthesizes everything

### Version Control

Use git from the start. Commit after each completed lesson. This gives you a timeline of your learning journey and makes it easy to go back and review earlier work.

---

## Community Resources and Where to Ask Questions

Working through this curriculum alone is possible but harder than it needs to be. Here are places to find help and community.

### When You Are Stuck on a Concept

- **Stack Overflow** for specific technical questions ("PyTorch error: expected 3D tensor but got 2D")
- **The PyTorch forums** (discuss.pytorch.org) for PyTorch-specific questions. The community is active and helpful.
- **r/MachineLearning** and **r/LearnMachineLearning** on Reddit for broader ML questions and discussions

### When You Want Deeper Understanding

- **3Blue1Brown** on YouTube for visual intuition about linear algebra, calculus, and neural networks
- **Andrej Karpathy's YouTube channel** for excellent walkthroughs of neural network implementations
- **Yannic Kilcher's YouTube channel** for paper explanations and discussions of current research

### When You Want Community

- **Discord servers** for ML communities (EleutherAI, HuggingFace, MLOps Community) often have channels for beginners and study groups
- **Local meetups** if you are in a city with an active tech community. Working through hard material with other people makes it both easier and more enjoyable.
- **Study groups**: consider finding one or two other people working through this curriculum at a similar pace. A weekly check-in where you discuss what you built, what confused you, and what you learned is remarkably effective.

Once you are past the midpoint of the curriculum, consider contributing back: write blog posts about concepts you struggled with, answer questions on forums, or open-source your implementations. Teaching solidifies understanding, and the community benefits from practitioners who have worked through the material honestly.

---

## Hardware Requirements at Different Phases

This is covered in detail in the **Recommended Hardware Progression** appendix, but here is the summary for planning purposes:

- **Phases 1-3**: A laptop CPU or free Google Colab is sufficient. Do not spend money on hardware yet.
- **Phases 4-6**: A single GPU is strongly recommended. An RTX 4090, an A100, or Colab Pro will work. Cloud rentals on RunPod or Vast.ai cost $1-2 per hour.
- **Phases 7-10**: Multi-GPU access becomes useful for distributed training exercises. Cloud instances with 2-4 GPUs are ideal. Budget $200-500 for this stretch if using cloud resources.
- **Phases 11-14**: Hardware needs depend on your chosen capstone project. By this point, you will know exactly what you need and how to get it cost-effectively.

The key principle: **start cheap and scale only when you actually hit a wall.** Many people over-invest in hardware before they need it. The best GPU in the world will not teach you backpropagation faster than a CPU will. Save your money (or your cloud budget) for the phases where compute actually matters.

---

## Practical Tips That Do Not Fit Elsewhere

A collection of advice from experience.

### On Note-Taking

Keep a simple running log. Date, lesson, what you worked on, what you learned, what confused you. This takes five minutes per session and pays dividends when you need to review. Do not build an elaborate note-taking system -- that is procrastination disguised as productivity.

### On Breaks

Take them. If you have been coding for three hours and you are stuck, walk away for 15 minutes. Your subconscious will keep working on the problem. Some of the best debugging breakthroughs happen when you are not staring at the screen.

### On Imposter Syndrome

You will feel it. You will read about someone who implemented a transformer in a weekend and feel inadequate. Remember: their weekend project was built on years of prior experience that you are currently building. Your pace is your pace.

### On Diminishing Returns

Not every lesson requires the same depth of engagement. Phase 1 deserves disproportionate time because everything builds on it. By Phase 10, you will have enough experience to move through material faster. Trust this acceleration -- it is a sign that the foundations are solid.

### On Rereading Earlier Material

After completing Phase 5, go back and reread the core concepts from Phase 1. You will be amazed at how differently they read now. Concepts that seemed abstract will feel obvious. Connections you missed will jump out. This is not wasted time -- it is consolidation.

### On Perfectionism

Your first implementation of anything will be ugly. That is fine. Make it work first. Make it correct. Make it clean later, if and when you have a reason to. A messy implementation that you understand completely is worth far more than a clean implementation you copied from GitHub.

---

## Before You Begin: A Checklist

Use this to verify you are ready to start Lesson 1.1:

- Python 3.10 or later is installed and working
- You have a virtual environment set up for this curriculum
- PyTorch is installed (CPU-only is fine for now)
- Jupyter is installed and you can open a notebook
- NumPy, Matplotlib, and tqdm are installed
- You have a directory structure ready for organizing your work
- You have git initialized in your project directory
- You can write a Python class, use list comprehensions, and debug with print statements
- You know what matrix multiplication does and what `Wx + b` means
- You understand derivatives and the chain rule at an intuitive level
- You have blocked out regular time in your schedule for the next several months

If everything on this list is checked, you are ready. Navigate to Phase 1, Lesson 1.1, and start building. The journey is long, but every step forward is a step you keep.
