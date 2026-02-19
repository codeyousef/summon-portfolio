---
title: "Overview"
section_id: ""
phase: 0
phase_title: ""
order: 0
---

# Unified AI Curriculum: From Zero to Research Frontier

## A Learn-by-Doing Journey Through Modern Deep Learning

Welcome. This curriculum will take you from writing your first neural network to implementing research-grade architectures, training large language models, building diffusion-based image generators, and exploring the cutting edge of AI. It is not a collection of lectures. It is a structured sequence of increasingly ambitious projects, each one building on the last, each one demanding that you write real code and understand every line of it.

By the end, you will have built -- with your own hands -- transformers, diffusion models, state space models, alignment pipelines, multi-agent systems, and more. You will not just know what these things are. You will know how they work, why they work, and where they break.

---

## Philosophy

### Learn by Building, Not by Reading

There is a common failure mode in self-directed AI education: reading paper after paper, watching lecture after lecture, accumulating a vast bibliography of things you have "covered" but cannot reproduce from memory. This curriculum takes a different approach.

Every concept in this curriculum is introduced through implementation. You do not read about backpropagation and then move on. You implement automatic differentiation from scratch, verify your gradients against PyTorch to six decimal places, and then train a model with your own engine. You do not read about attention mechanisms. You implement scaled dot-product attention with einsum, visualize attention weights, and watch heads specialize.

This is harder and slower than reading. That is the point. Understanding forged through struggle is understanding that lasts.

### Productive Struggle Is the Method

When you hit a wall -- and you will -- resist the urge to immediately look at a solution. Spend 30 to 60 minutes working through the problem. Try different approaches. Read the error messages carefully. Print intermediate values. Draw the computation graph on paper. The time you spend stuck is not wasted time. It is the time when learning actually happens.

After a genuine attempt, looking at a reference implementation is not cheating. It is the next step. But the attempt must come first.

### Depth Over Breadth

This curriculum is long, but it is not in a hurry. If Phase 1 takes you three weeks instead of one, that is fine. If you need to revisit matrix multiplication after Phase 3 because something about attention did not click, do it. Solid foundations matter more than a fast pace. You cannot build a transformer if your understanding of gradient flow is shaky.

### Implementation as Understanding

There is a specific kind of understanding that comes only from implementation. You can read that "RMSNorm divides by the root mean square of activations" and nod. But when you implement it, you confront real questions: Do I compute the mean before or after squaring? What is the shape of the scale parameter? What happens at inference time versus training time? These details are where real understanding lives.

### The Compounding Advantage

Each phase in this curriculum builds directly on the last. The autograd engine you build in Phase 1 gives you intuition that makes Phase 3's transformer implementation feel natural instead of magical. The attention visualizations you create in Phase 2 prepare you to debug the multi-head attention in Phase 3. The training loop discipline from Phase 1 means you can troubleshoot alignment training in Phase 4 without flailing.

This compounding effect is the curriculum's greatest strength, and it is also why skipping ahead is so costly. Every shortcut you take in the foundations will slow you down later, when the systems become more complex and the debugging becomes harder.

### Failure Is Data

Your first training run will not converge. Your first attention implementation will have a shape mismatch. Your first diffusion model will produce noise that looks nothing like images. These failures are not setbacks. They are the curriculum working as intended.

Every bug you encounter and fix teaches you something that no textbook can convey. When your loss suddenly spikes to NaN, you learn about numerical stability. When your model memorizes the training set, you learn about regularization not as an abstract concept but as a concrete tool you needed. When your generated text loops incoherently, you learn about temperature and sampling in a way that sticks.

---

## How This Curriculum Is Structured

Each lesson in the curriculum follows a consistent format with four types of content. Understanding these will help you get the most out of every section.

### Core Concepts

Every lesson begins with the conceptual foundation. This is the "what" and the "why" -- the key ideas, the mathematical intuitions, the architectural decisions, and the reasons behind them. These are kept concise. The goal is to give you enough to understand what you are about to build, not to exhaustively survey the literature.

Read these carefully, but do not expect them to make full sense until you have implemented the build-along. That is normal. Concepts click when your code runs.

### Build-Along Projects

This is where the real learning happens. Every lesson has at least one substantial implementation project. These are not toy exercises -- they are real systems, simplified enough to be tractable but complete enough to teach you something genuine.

Build-alongs are not optional. If you skip them, you are not doing this curriculum. You are reading a long document. The difference matters enormously.

Some build-alongs take an afternoon. Some take several days. Budget time accordingly.

### Checkpoints

Checkpoints are concrete, measurable criteria that tell you whether your implementation is correct and whether you understand the material. They take forms like:

- "Your gradients should match PyTorch to 6 decimal places."
- "Your model should produce coherent Shakespeare-like sentences after 30 minutes on a single GPU."
- "Plot memory usage vs. sequence length. You should see quadratic growth for transformers and linear growth for Mamba."

Do not move past a checkpoint until you have met it. If your gradients only match to 3 decimal places, something is wrong. Find it. Fix it. That debugging process is part of the curriculum.

### Guided Exercises

Guided exercises appear where they serve learning, not as busywork. They typically appear in one of three scenarios:

1. **Debugging practice**: You are given a broken training loop and must diagnose the problem. This is a real skill that papers do not teach.
2. **Ablation studies**: You systematically vary a parameter (LoRA rank, temperature, guidance scale) and measure the effect. This builds intuition about what matters and what does not.
3. **Exploration**: You investigate emergent behavior (expert specialization in MoE, induction head formation, attention pattern evolution) that cannot be learned from reading alone.

---

## What You Will Need

### Prerequisites

This curriculum assumes:

**Python proficiency.** You should be comfortable writing classes, using list comprehensions, debugging with print statements, and navigating the standard library. You do not need to be an expert, but you should not be learning Python syntax at the same time you are learning backpropagation.

**Basic linear algebra.** Matrix multiplication, dot products, transpose, inverse. You should know what it means to multiply a matrix by a vector and what the resulting shape is. If the notation Wx + b makes sense to you, you are fine. If not, spend a week with 3Blue1Brown's "Essence of Linear Algebra" series before starting.

**Calculus intuition.** You need to understand derivatives as rates of change and the chain rule as composition of rates. You do not need to be able to solve integrals by hand. If you understand that the derivative of f(g(x)) is f'(g(x)) * g'(x), that is sufficient. The rest you will pick up as needed.

**Comfort with the command line.** You will be running Python scripts, managing virtual environments, and occasionally using git. Nothing exotic, but you should not be frightened by a terminal.

You do **not** need:
- Prior machine learning experience
- A GPU (for the first several phases)
- Advanced mathematics (measure theory, abstract algebra, etc.)
- Experience with any specific ML framework

### Hardware

The curriculum is designed to be accessible:

- **Phases 1-3**: A laptop CPU or Google Colab free tier is sufficient. You are training small models on small datasets.
- **Phases 4-6**: A single GPU is strongly recommended. An RTX 4090, an A100 40GB, or Colab Pro will work. Cloud options like Lambda Labs or RunPod are cost-effective alternatives.
- **Phases 7-10**: Multi-GPU access becomes useful for distributed training exercises. Cloud instances with 2-4 GPUs are ideal.
- **Phases 11-14**: Hardware needs depend on your chosen projects. The capstone phase is flexible by design.

Do not let hardware be a blocker. Colab, Kaggle notebooks, and cloud providers make GPU access cheaper than ever. Many build-alongs include notes on adapting to limited hardware.

### Software Environment

Set up the following before starting Phase 1:

- **Python 3.10+** with a virtual environment (venv or conda)
- **PyTorch 2.0+** (install with CUDA support if you have a GPU)
- **Jupyter Notebook or JupyterLab** for interactive experimentation
- **NumPy, Matplotlib, tqdm** for array operations, plotting, and progress bars
- **A text editor or IDE** you are comfortable with (VS Code, PyCharm, vim -- whatever you think in)

Additional libraries will be introduced as needed in later phases. Each lesson lists its specific dependencies. Do not install everything upfront; install as you go.

### Recommended Approach to Each Lesson

Before diving into the phases, here is a practical workflow that works well:

1. **Read the Core Concepts section once**, without trying to memorize anything. Get the shape of the ideas.
2. **Start the Build-Along immediately.** Open your editor. Create a new file. Begin writing code.
3. **Refer back to Core Concepts as you build.** The concepts will make more sense now that you have concrete questions.
4. **Hit the Checkpoint.** Run your tests. Compare your numbers. Do not proceed until they match.
5. **Do the Guided Exercise if one exists.** These are where intuition is built.
6. **Reflect briefly.** What surprised you? What was harder than expected? What would you do differently? Even a few sentences in a personal log helps consolidation.

---

## Roadmap: The 14 Phases

Here is what you will build across this curriculum, phase by phase.

### Phase 1: Foundations (Weeks 1-3)

Build your first neural networks from scratch. You will start with NumPy-level data loading -- reading raw CIFAR-10 binary files, implementing batching and shuffling without any framework help. Then you will build an automatic differentiation engine (Micrograd) in approximately 100 lines of code, implementing the `Value` class with arithmetic operators and reverse-mode autodiff. Finally, you will train an MNIST classifier using explicit layers you write yourself, without `nn.Sequential`, including Linear, ReLU, and CrossEntropy from basic tensor operations. By the end of this phase, you will understand every line in a training loop because you will have written each one yourself.

### Phase 2: Core Architectures (Weeks 4-6)

Master the building blocks that power modern AI. You will implement a full U-Net with encoder, bottleneck, decoder, and skip connections for image denoising. You will build an LSTM cell from scratch and train a character-level language model on a book, generating text at different temperatures and comparing LSTM, GRU, and vanilla RNN performance. You will implement the complete attention mechanism -- query-key-value projections, scaled dot-product, multi-head splitting -- using einsum notation, and visualize which tokens attend to which. This phase bridges the gap between simple feedforward networks and the architectures that actually power the field.

### Phase 3: Transformers (Weeks 7-9)

Build a GPT-style language model from the ground up in approximately 300 lines of code. Implement token and position embeddings, multi-head causal self-attention with masking, feed-forward networks with GELU activation, layer normalization, and residual connections. Implement BPE tokenization. Train on TinyShakespeare with learning rate warmup and cosine decay. Build an interactive text generator with temperature control, top-p sampling, and KV caching. By the end, you will be able to implement a decoder-only transformer from memory.

### Phase 4: Large Language Models (Weeks 10-12)

Enter the world of modern LLMs. Implement the LLaMA architecture with its specific innovations: RMSNorm instead of LayerNorm, SwiGLU activation instead of GELU, and Rotary Position Embeddings for better length generalization. Then learn parameter-efficient fine-tuning by implementing LoRA -- decomposing weight updates into low-rank matrices -- and QLoRA for memory-efficient training on consumer hardware. Finally, implement Direct Preference Optimization to align model behavior with human preferences without the complexity of full RLHF. You will also study scaling laws and understand what it means for a model to be "Chinchilla-optimal."

### Phase 5: Diffusion Models (Weeks 13-15)

Build generative models that create images from pure noise. Implement the complete DDPM pipeline from scratch: the forward process that gradually adds Gaussian noise, a U-Net denoising model conditioned on timestep, and the reverse sampling loop that iteratively removes noise. Then implement DDIM for deterministic sampling in fewer steps, add classifier-free guidance for controllable generation, and train class-conditional models on CIFAR-10. You will explore the guidance scale parameter and discover where "too perfect" samples lose diversity. Watch images emerge from pure static -- it never stops being remarkable.

### Phase 6: State Space Models (Weeks 16-17)

Understand the Mamba architecture and linear-time sequence modeling. Implement the S4 layer with HiPPO initialization, then build a selective state space model with input-dependent parameters. Benchmark memory usage against transformers and see the linear vs. quadratic scaling difference firsthand.

### Phase 7: Advanced Architectures (Weeks 18-20)

Explore Mixture of Experts with top-k routing and load balancing. Implement Performer attention with random Fourier features. Build a multimodal image captioning system with a frozen CLIP encoder and a trained decoder transformer. These are the architectural innovations that push the field forward.

### Phase 8: Training at Scale (Weeks 21-23)

Learn to train models that do not fit on a single GPU. Apply gradient checkpointing, mixed precision, and ZeRO optimization to progressively reduce memory requirements. Set up distributed data parallelism. Benchmark Flash Attention at different sequence lengths. Understand the memory hierarchy that governs modern training.

### Phase 9: Interpretability and Analysis (Weeks 24-25)

Open the black box. Train sparse autoencoders on model activations to extract interpretable features. Build linear probes to detect capabilities across layers. Use the logit lens to read a model's "thoughts" at intermediate layers. Find induction heads and watch them emerge during training.

### Phase 10: Efficiency and Deployment (Weeks 26-27)

Make models practical. Quantize weights to 4-bit with GPTQ and measure the perplexity-speed tradeoff. Implement speculative decoding with a small draft model and a large target model. Distill a large model into a small one and measure what knowledge transfers.

### Phase 11: Advanced Generation (Weeks 28-29)

Go beyond standard diffusion. Implement flow matching with straight-line trajectories. Distill a consistency model for single-step generation. Build a VQ-VAE tokenizer and train a transformer to generate images autoregressively. Compare these paradigms on the same data and understand their tradeoffs.

### Phase 12: Agentic AI and Reasoning (Weeks 30-31)

Build systems that think, plan, and act. Implement Tree of Thoughts for structured reasoning over math problems. Build a ReAct-style agent that decides when to use a calculator tool. Construct a multi-agent debate system where adversarial discussion improves factual accuracy.

### Phase 13: Research Frontiers (Weeks 32-34)

Engage with the cutting edge. Implement StreamingLLM for efficient long-context inference. Build a Test-Time Training layer that adapts at inference. Use ROME to surgically edit a single fact inside a language model and measure the side effects. These are active research areas where the answers are still being discovered.

### Phase 14: Capstone Projects (Weeks 35-40)

Demonstrate mastery with a substantial project of your choosing. Four options are provided, each requiring synthesis across multiple phases: (A) train a production-quality LLM from data curation through RLHF to API deployment; (B) build a text-conditioned latent diffusion image generator with classifier-free guidance and a web demo; (C) replicate key results from a recent paper and publish your findings; or (D) design, implement, benchmark, and ablate a novel architecture variant. This is where everything comes together. The capstone is not a test. It is the reason you did all of this.

---

## Pacing and Expectations

This curriculum is designed to take roughly 40 weeks at a steady pace. That is not a deadline. Some people will move faster. Many will move slower, especially if they are learning part-time alongside other commitments.

A realistic weekly commitment looks like:

- **10-15 hours per week**: Comfortable pace with time for exploration and re-reading.
- **20+ hours per week**: Aggressive pace suitable for full-time study. You will finish faster and have time for deeper dives.
- **5-8 hours per week**: Slower but entirely viable. Extend the timeline proportionally. Consistency matters more than intensity.

The phases are listed with week ranges, but these are rough guides, not prescriptions. The only real rule is: do not move forward until you understand where you are. A shaky Phase 1 will haunt you in Phase 3.

---

## The Learning Arc

It helps to see the curriculum as three broad arcs:

**Arc 1 -- Foundations and Fluency (Phases 1-5):** You learn the core vocabulary of deep learning. By the end you can implement, train, and debug transformers and diffusion models. You know what a training loop does, how gradients flow, what attention computes, and how noise becomes images. This is where most of your time will go, and it is the most important arc.

**Arc 2 -- Breadth and Scale (Phases 6-10):** You expand outward. New architectures (state space models, mixture of experts, multimodal systems), new scales (multi-GPU training, Flash Attention), new perspectives (interpretability, quantization, distillation). These phases fill in the landscape of modern AI research and engineering.

**Arc 3 -- Frontiers and Mastery (Phases 11-14):** You engage with the cutting edge and prove your skills. Advanced generation methods, agentic systems, active research problems, and finally a capstone project that demands everything you have learned. By the end of Arc 3, you are not a student. You are a practitioner.

Understanding this arc structure helps when you feel lost in the middle of Phase 7 or overwhelmed by Phase 10. You can always zoom out and see where you are on the larger journey.

---

## Key Libraries You Will Use

You will gradually accumulate a toolkit as you progress:

- **PyTorch** -- the core framework for everything. You will use it from Phase 1 onward.
- **NumPy** -- for the initial from-scratch implementations before you graduate to PyTorch.
- **HuggingFace Transformers** -- for loading pretrained models in later phases (fine-tuning, alignment).
- **DeepSpeed** -- for distributed training and ZeRO optimization in Phase 8.
- **Flash Attention** -- for efficient attention kernels in Phases 8+.
- **PEFT / LoRA** -- for parameter-efficient fine-tuning in Phase 4.
- **vLLM** -- for fast inference and serving in Phase 10.
- **Matplotlib / Weights & Biases** -- for visualization and experiment tracking throughout.

Do not install these all at once. Each phase introduces what you need when you need it.

---

## A Note on Using AI Assistants

You are learning AI by building AI. It is natural to wonder whether you should use AI assistants (ChatGPT, Claude, Copilot) while working through this curriculum.

The short answer: use them as a last resort for understanding, never as a first resort for implementation.

When you are genuinely stuck on a concept and documentation is not helping, asking an AI to explain "why does RMSNorm divide by the RMS instead of subtracting the mean" is fine. That is using a tool to learn.

When you paste a build-along specification into an AI and ask it to write the code, you have skipped the only part that matters. The code is not the product. Your understanding is the product. The code is just evidence that understanding exists.

A good heuristic: if you could not re-implement what the AI gave you from memory after reading it, you did not learn it.

---

## Common Pitfalls

People who attempt curricula like this tend to fail in predictable ways. Knowing the failure modes helps you avoid them.

**Tutorial hell.** You read the core concepts, watch a YouTube video about the same topic, read a blog post, find a second blog post, and never open your editor. The build-along is the curriculum. Everything else is preamble.

**Skipping foundations.** Phase 3 feels exciting. Phase 1 feels basic. So you jump ahead. Then you spend three days debugging an attention implementation because you do not understand how autograd handles in-place operations. Go in order.

**Perfectionism before understanding.** You want your code to be clean, well-documented, and modular before it works. Write messy code first. Make it work. Make it correct. Then, and only then, make it clean. Premature abstraction is the enemy of learning.

**Comparing yourself to others.** Someone on Twitter built a transformer in a weekend. They probably had three years of prior experience. Your pace is your pace. The only comparison that matters is: do you understand more today than you did yesterday?

**Giving up at the plateau.** Learning curves are not linear. You will have periods of rapid progress followed by frustrating plateaus where nothing seems to click. The plateau is where consolidation happens. Push through it. The next breakthrough is on the other side.

---

## What Success Looks Like

By the time you complete this curriculum, you will be able to:

- **Implement a transformer from scratch** in under an hour, from memory, without reference material. Not because you memorized code, but because you understand every component deeply enough to derive it.
- **Read a research paper** and assess its claims, identify its contributions relative to prior work, and estimate how difficult it would be to reproduce.
- **Train and fine-tune language models** using modern techniques (LoRA, DPO, mixed precision) and understand the engineering tradeoffs at each decision point.
- **Build generative models** for images using diffusion, flow matching, or autoregressive methods, and explain why you would choose one over another for a given application.
- **Debug training failures** by reasoning about gradient flow, numerical stability, data quality, and hyperparameter sensitivity -- not by randomly tweaking settings.
- **Reason about efficiency** at every level: algorithmic complexity, memory hierarchy, quantization tradeoffs, distributed communication overhead.
- **Engage with current research** as a participant, not just an observer. You will have the vocabulary, the intuition, and the implementation skills to contribute.

This is not a certificate program. There is no credential at the end. The credential is what you can build.

---

## Getting Started

You are here, reading the overview. Good. Now begin.

Navigate to **Phase 1, Lesson 1.1: Python and NumPy for ML**. Read the core concepts. Set up your Python environment with PyTorch and NumPy. Create your first project directory. Start building.

Every expert was once a beginner who refused to stop. The only way out is through.
