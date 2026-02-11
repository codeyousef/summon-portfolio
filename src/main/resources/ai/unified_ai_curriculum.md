# Unified AI Curriculum: From Zero to Research Frontier

## A Learn-by-Doing Journey Through Modern Deep Learning

---

## Philosophy

This curriculum is designed for **learning by building**. Each section includes:

- **Core Concepts**: What you need to understand
- **Build-Along Project**: Implement it yourself (not optional)
- **Deep Dive**: Advanced topics when relevant
- **Checkpoint**: Verify understanding before proceeding

**Guided exercises appear only where they serve learning**—when implementing a complex algorithm, debugging a training
loop, or exploring emergent behaviors. No "homework for homework's sake."

---

# Phase 1: Foundations (Weeks 1-3)

*Goal: Build your first neural networks from scratch. Understand every line of code.*

## 1.1 Python & NumPy for ML

### Core Concepts

- List comprehensions, generators for data pipelines
- NumPy broadcasting and memory layout (C-order vs Fortran-order)
- Efficient array operations for batch processing

### Build-Along: Custom Data Pipeline

Implement a data loader from scratch without PyTorch:

- Read CIFAR-10 from raw binary files
- Implement batching, shuffling, and augmentation
- Compare your speed to PyTorch's DataLoader

**Checkpoint**: Your pipeline should handle 50,000 images in <2 seconds per epoch on CPU.

---

## 1.2 PyTorch Fundamentals

### Core Concepts

- Tensors: creation, operations, GPU movement
- Autograd: computational graphs, `backward()`, `requires_grad`
- `nn.Module`: parameter registration, custom layers
- Optimizers: SGD, Adam, learning rate scheduling

### Build-Along: Micrograd from Scratch

Implement automatic differentiation in ~100 lines:

- Define `Value` class with `__add__`, `__mul__`, `__pow__`
- Implement reverse-mode autodiff
- Train a tiny MLP on XOR
- Compare gradients to PyTorch's autograd

**Checkpoint**: Your gradients should match PyTorch to 6 decimal places.

---

## 1.3 Your First Neural Network

### Core Concepts

- MLPs, activation functions, initialization schemes
- Forward pass, loss computation, backward pass, optimizer step
- Train/validation split, overfitting detection

### Build-Along: MNIST Classifier (Pure PyTorch)

Build without `nn.Sequential`—explicit layers:

- Implement Linear, ReLU, CrossEntropy from basic operations
- Train to >95% accuracy
- Visualize learned weights as images
- Add dropout, observe regularization effect

**Guided Exercise**: Debug a training loop where loss isn't decreasing. Common bugs: forgot `zero_grad()`, wrong loss
function, learning rate too high, forgot to call `.to(device)`.

---

# Phase 2: Core Architectures (Weeks 4-6)

*Goal: Understand the building blocks that power modern AI.*

## 2.1 Convolutional Networks

### Core Concepts

- Conv2d: kernels, padding, stride, dilation
- Pooling, batch normalization
- Receptive field calculation
- U-Net architecture with skip connections

### Build-Along: U-Net from Scratch

Implement the full architecture:

- Encoder with downsampling
- Bottleneck
- Decoder with skip connections
- Test on a simple denoising task

**Checkpoint**: Visualize feature maps at each layer. Do you see edge detectors in early layers, textures in middle,
objects in late?

---

## 2.2 Sequence Modeling

### Core Concepts

- RNNs, hidden states, BPTT
- LSTM/GRU gating mechanisms
- Encoder-decoder architectures
- Teacher forcing vs. autoregressive generation

### Build-Along: Character-Level Language Model

Train on your favorite book:

- Implement LSTM cell from scratch
- Generate text at different temperatures
- Observe how context window affects coherence
- Compare LSTM vs GRU vs vanilla RNN

**Guided Exercise**: Implement beam search decoding. Compare greedy (argmax) vs beam search outputs. When does beam
search help most?

---

## 2.3 Attention Mechanisms

### Core Concepts

- Query-Key-Value intuition
- Scaled dot-product attention
- Multi-head attention
- Self-attention vs cross-attention
- Causal masking for autoregressive models

### Build-Along: Attention Visualizer

Implement attention from scratch with einsum:

```python
scores = torch.einsum('bqd,bkd->bqk', Q, K) / sqrt(d_k)
attn_weights = F.softmax(scores, dim=-1)
output = torch.einsum('bqk,bkd->bqd', attn_weights, V)
```

- Train a tiny transformer on a toy translation task
- Visualize attention weights
- Observe which tokens attend to which

**Checkpoint**: Can you see attention heads specializing? (syntax, coreference, positional)

---

# Phase 3: Transformers (Weeks 7-9)

*Goal: Build and train a GPT-style language model from scratch.*

## 3.1 Complete Transformer Implementation

### Core Concepts

- Encoder stack: multi-head self-attention + FFN
- Decoder stack: masked self-attention + cross-attention + FFN
- Positional encodings (sinusoidal vs learned)
- Pre-LN vs Post-LN architectures

### Build-Along: NanoGPT

Implement a complete decoder-only transformer (~300 lines):

- Token and position embeddings
- Multi-head attention with causal masking
- Feed-forward network with GELU
- Layer normalization and residual connections
- Training loop with gradient clipping

**Guided Exercise**: Profile your implementation. Where is time spent? (hint: usually attention). Implement Flash
Attention if on compatible GPU.

---

## 3.2 Training Language Models

### Core Concepts

- Next-token prediction objective
- Tokenization (BPE, SentencePiece)
- Learning rate schedules (warmup, cosine decay)
- Gradient accumulation for large effective batch sizes
- Mixed precision training (FP16/BF16)

### Build-Along: Train on TinyShakespeare

- Tokenize with BPE (use tiktoken or implement basic BPE)
- Train a 10M parameter model
- Generate samples at different checkpoints
- Observe how quality improves with training

**Checkpoint**: Your model should produce coherent Shakespeare-like sentences after ~30 minutes on a single GPU.

---

## 3.3 Inference & Generation

### Core Concepts

- Greedy decoding, beam search, nucleus sampling
- Temperature scaling
- Key-value caching for efficient generation
- Top-p (nucleus) and top-k sampling

### Build-Along: Interactive Text Generator

Build a CLI tool with options:

- Temperature control (0.1 to 2.0)
- Top-p sampling
- Max tokens
- Stop sequences

**Guided Exercise**: Generate 100 samples with different temperatures. At what temperature does coherence break down? At
what temperature does diversity suffer?

---

# Phase 4: Large Language Models (Weeks 10-12)

*Goal: Understand how to train, fine-tune, and align LLMs.*

## 4.1 Modern LLM Architectures

### Core Concepts

- GPT-style causal masking
- LLaMA optimizations: RMSNorm, SwiGLU, RoPE
- Scaling laws (Chinchilla-optimal training)
- Rotary Position Embeddings (RoPE)

### Build-Along: Implement LLaMA Architecture

Build with modern components:

- RMSNorm instead of LayerNorm
- SwiGLU activation
- RoPE for positional encoding
- Compare to standard transformer

**Checkpoint**: Verify your implementation matches reference outputs on same weights (if using pretrained).

---

## 4.2 Fine-Tuning & Adaptation

### Core Concepts

- Supervised Fine-Tuning (SFT)
- LoRA (Low-Rank Adaptation)
- QLoRA for memory-efficient training
- Instruction tuning format

### Build-Along: Fine-Tune for a Task

- Take a pretrained model (GPT-2 or Pythia)
- Prepare instruction-following data
- Fine-tune with LoRA
- Compare full fine-tune vs LoRA performance

**Guided Exercise**: Ablate LoRA rank (4, 8, 16, 64). At what rank do you see diminishing returns? Measure trainable
parameters vs performance.

---

## 4.3 Alignment & RLHF

### Core Concepts

- Reward modeling from preferences
- PPO for policy optimization
- DPO (Direct Preference Optimization)
- Constitutional AI and AI feedback

### Build-Along: Implement DPO

The simpler alternative to RLHF:

- Prepare preference pairs (win/loss)
- Implement DPO loss
- Fine-tune a model
- Compare to SFT baseline

**Checkpoint**: Can you steer model behavior with preference data? Try helpfulness vs harmlessness tradeoffs.

---

# Phase 5: Diffusion Models (Weeks 13-15)

*Goal: Build generative models that create images from noise.*

## 5.1 Diffusion Fundamentals

### Core Concepts

- Forward process: Gaussian noise schedule
- Reverse process: learned denoising
- Noise prediction objective
- Variance preservation

### Build-Along: DDPM from Scratch

Implement the full pipeline:

- Forward process (add noise at timestep t)
- U-Net denoising model with time conditioning
- Training loop sampling random timesteps
- Sampling loop (ancestral sampling)

**Guided Exercise**: Train on MNIST first (fast iteration). Then try CIFAR-10 or a simple dataset of your choice.

---

## 5.2 Accelerated Sampling

### Core Concepts

- DDIM: deterministic sampling with fewer steps
- Classifier-free guidance
- Latent diffusion models (LDM)

### Build-Along: Implement DDIM

- Modify sampling to use DDIM
- Compare 1000-step DDPM vs 50-step DDIM
- Implement classifier-free guidance
- Explore guidance scale effect on quality/diversity

**Checkpoint**: At what guidance scale do samples become "too perfect" and lose diversity?

---

## 5.3 Conditional Generation

### Core Concepts

- Class conditioning
- Text conditioning via cross-attention
- CFG (Classifier-Free Guidance) scale

### Build-Along: Class-Conditional Diffusion

- Add class embedding to your U-Net
- Train on CIFAR-10 with labels
- Generate samples for each class
- Interpolate between classes

---

# Phase 6: State Space Models (Weeks 16-17)

*Goal: Understand the Mamba architecture and linear-time sequence modeling.*

## 6.1 State Space Model Foundations

### Core Concepts

- Continuous-time dynamics: A, B, C, D matrices
- Discretization with step size Δ
- HiPPO framework for memorization
- Linear time-invariant systems

### Build-Along: S4 Layer Implementation

Implement the basic S4 layer:

- HiPPO initialization of A matrix
- Discretization to discrete steps
- Convolutional representation for training
- Recurrent representation for inference

---

## 6.2 Selective State Space Models (Mamba)

### Core Concepts

- Input-dependent B, C, Δ parameters
- Hardware-aware parallel scan
- Selective memory (focus on relevant, ignore irrelevant)
- Mamba block architecture

### Build-Along: Minimal Mamba (~200 lines)

```python
class MambaBlock(nn.Module):
    # In_proj → Split → Conv1d → SiLU → Selective SSM → Gating → Out_proj
```

- Implement selective scan (sequential for clarity)
- Compare to transformer on same sequence length
- Measure memory usage vs sequence length

**Checkpoint**: Plot memory vs sequence length for transformer (quadratic) vs Mamba (linear).

---

# Phase 7: Advanced Architectures (Weeks 18-20)

*Goal: Explore cutting-edge architectures and techniques.*

## 7.1 Mixture of Experts (MoE)

### Core Concepts

- Sparse routing: top-k expert selection
- Load balancing losses
- Expert parallelism
- Dense-to-sparse upcycling

### Build-Along: 8-Expert MoE Layer

- Replace FFN with 8 experts + router
- Implement top-2 routing
- Add load balancing loss
- Train and observe expert specialization

**Guided Exercise**: Visualize which tokens route to which experts. Do experts specialize by topic, syntax, or something
else?

---

## 7.2 Linear Attention & Efficient Transformers

### Core Concepts

- FAVOR+ / Performer: kernel-based approximations
- Linear attention complexity: O(n·d²) vs O(n²·d)
- Hybrid architectures (some layers full, some linear)

### Build-Along: Implement Performer Attention

- Implement random Fourier features
- Replace standard attention in your transformer
- Benchmark speed at different sequence lengths
- Measure quality tradeoff

---

## 7.3 Multimodal Architectures

### Core Concepts

- Vision encoders (CLIP-style)
- Cross-modal attention
- Early vs late fusion
- Perceiver IO latent bottleneck

### Build-Along: Simple Image Captioning

- Use frozen CLIP encoder
- Train decoder transformer
- Implement cross-attention to image features
- Generate captions for your own images

---

# Phase 8: Training at Scale (Weeks 21-23)

*Goal: Learn to train models that don't fit on one GPU.*

## 8.1 Memory Optimization

### Core Concepts

- Gradient checkpointing (trade compute for memory)
- Mixed precision training
- ZeRO optimization stages
- Activation checkpointing strategies

### Build-Along: Train a Model That Doesn't Fit

- Start with a model that OOMs
- Apply gradient checkpointing
- Add mixed precision
- Use ZeRO-2 or ZeRO-3 with DeepSpeed

**Checkpoint**: Document memory reduction at each step. How large a model can you train on your hardware?

---

## 8.2 Distributed Training

### Core Concepts

- Data Parallelism (DDP)
- Tensor Parallelism
- Pipeline Parallelism
- 3D parallelism combinations

### Build-Along: Multi-GPU Training

- Set up DDP training
- Implement gradient synchronization
- Benchmark scaling efficiency (1 GPU → 2 → 4)
- Observe communication overhead

---

## 8.3 Flash Attention & Kernel Optimization

### Core Concepts

- IO-aware attention computation
- Tiling and online softmax
- Memory hierarchy optimization
- Kernel fusion

### Build-Along: Use Flash Attention 2

- Install and benchmark Flash Attention
- Compare memory usage vs standard attention
- Measure speedup at different sequence lengths
- Understand when it helps most

---

# Phase 9: Interpretability & Analysis (Weeks 24-25)

*Goal: Understand what's happening inside your models.*

## 9.1 Mechanistic Interpretability

### Core Concepts

- Sparse Autoencoders (SAEs)
- Feature extraction and dictionary learning
- Superposition problem
- Attribution methods (Integrated Gradients, attention rollout)

### Build-Along: SAE Feature Extraction

- Train a sparse autoencoder on GPT-2 activations
- Visualize learned features
- Find interpretable features (names, numbers, concepts)
- Use features for concept detection

---

## 9.2 Probing & Analysis

### Core Concepts

- Linear probing for capability detection
- Layer-wise analysis
- Attention pattern visualization
- Logit lens (projecting to vocabulary)

### Build-Along: Probe Your Model

- Train linear probes for POS tagging, NER
- Plot capability emergence across layers
- Use logit lens to "read" model's thoughts
- Find induction heads (copying patterns)

**Guided Exercise**: Find induction heads in your transformer. Do they emerge suddenly during training or gradually?

---

# Phase 10: Efficiency & Deployment (Weeks 26-27)

*Goal: Make models faster and deployable.*

## 10.1 Quantization

### Core Concepts

- Post-training quantization (INT8, INT4)
- GPTQ, AWQ, SmoothQuant
- Activation-aware quantization
- Tradeoffs: speed vs quality

### Build-Along: Quantize Your Model

- Apply GPTQ for 4-bit weights
- Benchmark perplexity vs FP16
- Measure inference speedup
- Try different bit widths (2-bit, 3-bit, 4-bit, 8-bit)

---

## 10.2 Speculative Decoding

### Core Concepts

- Draft-then-verify pattern
- Small draft model + large target model
- Acceptance rate and speedup
- Medusa: multiple heads without draft model

### Build-Along: Implement Speculative Decoding

- Use small model (125M) as draft
- Use larger model (1.5B) as target
- Measure acceptance rate
- Calculate end-to-end speedup

**Checkpoint**: At what draft size does overhead outweigh benefits?

---

## 10.3 Knowledge Distillation

### Core Concepts

- Teacher-student training
- Soft targets vs hard targets
- Distilling reasoning (STaR)
- MiniLLM discrepancy-aware distillation

### Build-Along: Distill a Small Model

- Train 1B parameter student from 7B teacher
- Compare standard KD vs on-policy distillation
- Measure quality vs compute tradeoff

---

# Phase 11: Advanced Generation (Weeks 28-29)

*Goal: Explore beyond standard diffusion.*

## 11.1 Flow Matching & Rectified Flow

### Core Concepts

- Velocity fields vs score functions
- Straight-line trajectories
- Fewer sampling steps
- Optimal transport connections

### Build-Along: Flow Matching Implementation

- Implement flow matching training
- Compare to diffusion on same data
- Measure sampling steps needed for quality

---

## 11.2 Consistency Models

### Core Concepts

- Single-step generation
- Consistency distillation
- Consistency training from scratch
- Quality vs speed tradeoffs

### Build-Along: Consistency Model Distillation

- Distill from your trained diffusion model
- Generate images in 1-4 steps
- Compare quality to multi-step diffusion

---

## 11.3 Autoregressive Image Generation

### Core Concepts

- VQ-VAE tokenization
- VQ-GAN improvements
- RQ-Transformer for residual quantization
- MAGVIT for video

### Build-Along: VQ-VAE + Transformer

- Train VQ-VAE on images
- Train transformer on discrete tokens
- Generate images autoregressively
- Compare to diffusion quality

---

# Phase 12: Agentic AI & Reasoning (Weeks 30-31)

*Goal: Build systems that think, plan, and act.*

## 12.1 Reasoning Architectures

### Core Concepts

- Chain-of-thought prompting
- Tree of Thoughts (ToT): search over reasoning paths
- Graph of Thoughts (GoT): non-linear reasoning
- Self-consistency decoding

### Build-Along: Implement ToT

- Generate multiple reasoning paths
- Evaluate intermediate states
- Use beam search for best path
- Apply to math word problems

---

## 12.2 Tool Use & Agents

### Core Concepts

- ReAct: reasoning + action interleaving
- Toolformer: learned API calls
- Retrieval-augmented generation (RAG)
- FLARE: active retrieval

### Build-Along: Build a Calculator Agent

- LLM decides when to use calculator
- Parse calculator calls from output
- Execute and return results
- Iterate until answer reached

**Guided Exercise**: Compare accuracy on math problems with vs without calculator tool.

---

## 12.3 Multi-Agent Systems

### Core Concepts

- Collaborative problem-solving
- Adversarial debate for truth
- Hierarchical agent structures
- Communication protocols

### Build-Along: Debate System

- Two agents argue opposite sides
- Judge agent evaluates
- Apply to fact-checking
- Measure accuracy improvement

---

# Phase 13: Research Frontiers (Weeks 32-34)

*Goal: Explore cutting-edge research areas.*

## 13.1 Long Context & Memory

### Core Concepts

- Ring Attention for million-token contexts
- StreamingLLM with sink tokens
- Compressive transformers
- Recurrent memory mechanisms

### Build-Along: StreamingLLM Implementation

- Implement KV cache eviction policy
- Preserve sink tokens
- Test on long-document QA
- Measure memory vs accuracy tradeoff

---

## 13.2 Test-Time Training

### Core Concepts

- TTT layers: learning at inference time
- Dual-timescale dynamics
- Closed-form updates (TTT-Linear)
- Domain adaptation without fine-tuning

### Build-Along: TTT-Linear Layer

- Implement recursive least squares update
- Compare adaptation speed vs standard layers
- Test on domain shift scenarios

---

## 13.3 Model Editing & Unlearning

### Core Concepts

- ROME: rank-one model editing
- Knowledge localization in MLP layers
- Gradient ascent for unlearning
- Negative Preference Optimization (NPO)

### Build-Along: Edit a Fact with ROME

- Locate factual knowledge in model
- Apply rank-one update
- Verify edit success
- Measure side effects on related facts

---

# Phase 14: Capstone Projects (Weeks 35-40)

*Goal: Build something impressive that demonstrates mastery.*

## Choose One:

### Option A: Train a Production-Quality LLM

- Curate high-quality training data
- Train 100M-1B parameter model
- Implement full RLHF pipeline
- Deploy with API

### Option B: Build a Diffusion Image Generator

- Train latent diffusion model
- Add text conditioning
- Implement classifier-free guidance
- Create web demo

### Option C: Research Replication

- Pick a recent paper (last 2 years)
- Replicate key results
- Document challenges and insights
- Publish findings

### Option D: Novel Architecture

- Design and implement a new variant
- Benchmark against baselines
- Ablate key components
- Write technical report

---

# Appendix: Quick Reference

## Recommended Hardware Progression

1. **Phase 1-3**: CPU or Colab free tier
2. **Phase 4-6**: Single GPU (RTX 4090 / A100 40GB)
3. **Phase 7-10**: Multi-GPU or cloud (Lambda, RunPod)
4. **Phase 11+**: As needed for project

## Key Libraries to Master

- PyTorch (core)
- Transformers (HuggingFace)
- DeepSpeed (distributed training)
- Flash Attention (efficient attention)
- LoRA/PEFT (parameter-efficient fine-tuning)
- vLLM (fast inference)

## Reading List (Papers)

**Must Read**:

- "Attention Is All You Need" (Transformer)
- "Language Models are Few-Shot Learners" (GPT-3)
- "LoRA: Low-Rank Adaptation"
- "Denoising Diffusion Probabilistic Models"
- "Mamba: Linear-Time Sequence Modeling"

**Recommended**:

- "Training Language Models to Follow Instructions" (InstructGPT)
- "Direct Preference Optimization"
- "FlashAttention: Fast and Memory-Efficient Exact Attention"
- "QLoRA: Efficient Finetuning of Quantized LLMs"

---

## How to Use This Curriculum

1. **Don't skip the build-alongs**. Understanding comes from implementation, not reading.
2. **Struggle productively**. Spend 30-60 minutes stuck before looking at solutions.
3. **Form study groups**. Explain concepts to others to solidify understanding.
4. **Write about what you learn**. Blog posts, Twitter threads, or notes.
5. **Contribute to open source**. Fix bugs, add features, improve docs.

**Remember**: The goal is deep understanding, not checking boxes. If a section takes longer, that's fine. If you need to
revisit fundamentals, do it. Quality over quantity, always.
