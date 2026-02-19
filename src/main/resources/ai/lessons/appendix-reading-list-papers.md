---
title: "Reading List (Papers)"
section_id: ""
phase: 0
phase_title: "Appendix"
order: 3
---

# Reading List (Papers)

This reading list is organized by importance, not by chronology. The "Must Read" papers are foundational -- they introduced ideas that everything else in this curriculum builds on. The "Recommended" papers extend those ideas in directions you will encounter during the later phases. The "Further Reading" section is grouped by topic for when you want to go deeper in a specific area.

Before diving in, read the section at the bottom on how to read ML papers effectively. It will save you significant time.

---

## Must Read

These papers are non-negotiable. Each one introduced a concept or technique that is now fundamental to modern deep learning. Read them carefully, in order, and re-read them after you have implemented the relevant techniques in the curriculum.

### Attention Is All You Need

**Citation:** Vaswani, A., Shazeer, N., Parmar, N., Uszkoreit, J., Jones, L., Gomez, A. N., Kaiser, L., & Polosukhin, I. (2017). "Attention Is All You Need." *Advances in Neural Information Processing Systems 30 (NeurIPS 2017)*.

**Key contribution:** Introduced the Transformer architecture, replacing recurrence and convolution entirely with self-attention mechanisms. Demonstrated that attention alone, combined with positional encodings and feed-forward layers, could achieve state-of-the-art results on machine translation while being significantly more parallelizable than RNNs.

**Why it matters:** This paper is the foundation of essentially all modern large language models, vision transformers, and multimodal architectures. Every model you build from Phase 4 onward traces its lineage to this paper.

**Curriculum connection:** Phase 4 (Lesson 4.1 - Building Transformers from Scratch). You will implement multi-head attention, positional encoding, and the full encoder-decoder architecture.

---

### Language Models are Few-Shot Learners (GPT-3)

**Citation:** Brown, T. B., Mann, B., Ryder, N., Subbiah, M., Kaplan, J., Dhariwal, P., Neelakanth, A., et al. (2020). "Language Models are Few-Shot Learners." *Advances in Neural Information Processing Systems 33 (NeurIPS 2020)*.

**Key contribution:** Demonstrated that scaling up language models to 175 billion parameters produces emergent in-context learning abilities -- the model can perform new tasks from a few examples in the prompt, without any gradient updates. Established the scaling laws paradigm: bigger models trained on more data with more compute systematically get better.

**Why it matters:** This paper shifted the field from task-specific fine-tuning to general-purpose foundation models. The idea that a single pretrained model can be steered by natural language prompts -- rather than by training on task-specific data -- underpins the entire modern LLM paradigm.

**Curriculum connection:** Phase 5 (Language Model Training). You will train your own smaller-scale language model and observe how capability scales with parameters and data.

---

### LoRA: Low-Rank Adaptation of Large Language Models

**Citation:** Hu, E. J., Shen, Y., Wallis, P., Allen-Zhu, Z., Li, Y., Wang, S., Wang, L., & Chen, W. (2022). "LoRA: Low-Rank Adaptation of Large Language Models." *International Conference on Learning Representations (ICLR 2022)*.

**Key contribution:** Proposed freezing the pretrained model weights and injecting trainable low-rank decomposition matrices into each transformer layer. This reduces the number of trainable parameters by 10,000x while matching or exceeding full fine-tuning quality on many tasks. LoRA adapters are small (a few MB), composable, and swappable.

**Why it matters:** LoRA made fine-tuning large models accessible to anyone with a single GPU. Before LoRA, fine-tuning a 7B parameter model required multiple A100s. With LoRA, you can do it on a consumer GPU with 16 GB of VRAM. It is now the default approach to model customization.

**Curriculum connection:** Phase 9 (Parameter-Efficient Fine-Tuning). You will implement LoRA from scratch, then use the PEFT library for production fine-tuning.

---

### Denoising Diffusion Probabilistic Models (DDPM)

**Citation:** Ho, J., Jain, A., & Abbeel, P. (2020). "Denoising Diffusion Probabilistic Models." *Advances in Neural Information Processing Systems 33 (NeurIPS 2020)*.

**Key contribution:** Showed that iteratively denoising Gaussian noise through a learned reverse process can generate high-quality images. Formalized the connection between diffusion processes and variational inference, and provided a simple training objective (predict the noise) that made diffusion models practical.

**Why it matters:** This paper launched the diffusion model revolution. Stable Diffusion, DALL-E 2, and Imagen all build directly on the framework introduced here. It demonstrated that likelihood-based generative models could match GANs in image quality without the training instability.

**Curriculum connection:** Phase 8 (Diffusion Models). You will implement the forward diffusion process, the denoising network, and the full sampling loop from this paper.

---

### Mamba: Linear-Time Sequence Modeling with Selective State Spaces

**Citation:** Gu, A. & Dao, T. (2023). "Mamba: Linear-Time Sequence Modeling with Selective State Spaces." *arXiv preprint arXiv:2312.00752*.

**Key contribution:** Introduced a selective state space model that achieves linear-time sequence modeling (O(N) vs the transformer's O(N^2)) by making the state space parameters input-dependent. This selectivity mechanism allows the model to filter information along the sequence, something fixed state space models cannot do. Matched transformer quality on language modeling benchmarks with significantly better throughput at long sequences.

**Why it matters:** Mamba represents the most credible challenge to the transformer's dominance. Whether state space models ultimately replace transformers or complement them, understanding this architecture is essential for anyone working at the frontier.

**Curriculum connection:** Phase 12 (Emerging Architectures). You will implement the selective state space mechanism and compare Mamba's behavior to transformers on sequence modeling tasks.

---

## Recommended

These papers deepen your understanding of specific techniques used throughout the curriculum. Read them when you reach the relevant phase, or earlier if you are curious.

### Training language models to follow instructions with human feedback (InstructGPT)

**Citation:** Ouyang, L., Wu, J., Jiang, X., Almeida, D., Wainwright, C., Mishkin, P., Zhang, C., et al. (2022). "Training language models to follow instructions with human feedback." *Advances in Neural Information Processing Systems 35 (NeurIPS 2022)*.

**Key contribution:** Described the three-stage alignment pipeline used to create InstructGPT: supervised fine-tuning on human demonstrations, reward model training on human preference comparisons, and reinforcement learning from human feedback (RLHF) using PPO. Showed that a 1.3B parameter aligned model was preferred by human evaluators over the 175B unaligned GPT-3.

**Why it matters:** This paper codified the alignment pipeline that every major LLM provider now uses. It demonstrated that alignment is not just a safety measure -- it actually makes models more useful.

**Curriculum connection:** Phase 11 (RLHF and Alignment). You will implement each stage of this pipeline.

---

### Direct Preference Optimization (DPO)

**Citation:** Rafailov, R., Sharma, A., Mitchell, E., Ermon, S., Manning, C. D., & Finn, C. (2023). "Direct Preference Optimization: Your Language Model is Secretly a Reward Model." *Advances in Neural Information Processing Systems 36 (NeurIPS 2023)*.

**Key contribution:** Showed that the RLHF objective can be reparameterized to eliminate the need for an explicit reward model and the PPO training loop. DPO directly optimizes the policy using a simple binary cross-entropy loss on preference pairs. This is mathematically equivalent to RLHF under certain assumptions, but dramatically simpler to implement and more stable to train.

**Why it matters:** DPO has become the preferred alignment method for many practitioners because it removes the most fragile components of the RLHF pipeline (reward model training and PPO). It is not strictly better in all cases, but it is better in most practical situations.

**Curriculum connection:** Phase 11 (RLHF and Alignment). You will implement DPO and compare it to PPO on the same task.

---

### FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness

**Citation:** Dao, T., Fu, D. Y., Ermon, S., Rudra, A., & Re, C. (2022). "FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness." *Advances in Neural Information Processing Systems 35 (NeurIPS 2022)*.

**Key contribution:** Redesigned the attention computation to be aware of the GPU memory hierarchy (SRAM vs HBM). By tiling the attention computation and keeping intermediate results in fast SRAM rather than writing the full N x N attention matrix to slow HBM, FlashAttention achieves 2-4x speedup and O(N) memory instead of O(N^2) -- while computing exact attention, not an approximation.

**Why it matters:** FlashAttention is now the default attention implementation in production systems. Understanding why it is fast (IO-awareness, not algorithmic cleverness) teaches you a crucial lesson about systems-level thinking in ML.

**Curriculum connection:** Phase 7 (Efficient Transformers). You will study the IO-awareness principle and integrate FlashAttention into your models.

---

### QLoRA: Efficient Finetuning of Quantized LLMs

**Citation:** Dettmers, T., Pagnoni, A., Holtzman, A., & Zettlemoyer, L. (2023). "QLoRA: Efficient Finetuning of Quantized LLMs." *Advances in Neural Information Processing Systems 36 (NeurIPS 2023)*.

**Key contribution:** Combined 4-bit NormalFloat quantization of the base model with LoRA adapters trained in BFloat16. Introduced double quantization (quantizing the quantization constants) and paged optimizers (using CPU memory as swap for optimizer states). This allowed fine-tuning a 65B parameter model on a single 48 GB GPU.

**Why it matters:** QLoRA democratized fine-tuning even further than LoRA. It made it possible to fine-tune the largest open models on consumer hardware, enabling a wave of community-created model variants.

**Curriculum connection:** Phase 9 (Parameter-Efficient Fine-Tuning) and Phase 10 (Quantization and Optimization).

---

## Further Reading by Topic

These are organized by subject area. Each list goes from introductory to advanced.

### Transformers and Attention

- Devlin et al. (2019). "BERT: Pre-training of Deep Bidirectional Transformers." -- Encoder-only transformers, masked language modeling.
- Raffel et al. (2020). "Exploring the Limits of Transfer Learning with a Unified Text-to-Text Transformer (T5)." -- Encoder-decoder, comprehensive study of pretraining objectives.
- Su et al. (2021). "RoFormer: Enhanced Transformer with Rotary Position Embedding." -- RoPE, now standard in most open LLMs.
- Shazeer (2020). "GLU Variants Improve Transformer." -- SwiGLU activation, used in LLaMA and most modern architectures.

### Scaling Laws and Training Dynamics

- Kaplan et al. (2020). "Scaling Laws for Neural Language Models." -- Power-law relationships between compute, data, model size, and loss.
- Hoffmann et al. (2022). "Training Compute-Optimal Large Language Models (Chinchilla)." -- Revised scaling laws: models are typically undertrained relative to their size.
- Clark et al. (2022). "Unified Scaling Laws for Routed Language Models." -- Scaling laws for mixture-of-experts.

### Diffusion Models

- Song et al. (2021). "Score-Based Generative Modeling through Stochastic Differential Equations." -- Unifying framework for score-based and diffusion models.
- Rombach et al. (2022). "High-Resolution Image Synthesis with Latent Diffusion Models." -- Latent diffusion (Stable Diffusion).
- Ho & Salimans (2022). "Classifier-Free Diffusion Guidance." -- The guidance technique that makes conditional generation work well.

### Alignment and Safety

- Bai et al. (2022). "Training a Helpful and Harmless Assistant from Human Feedback." -- Anthropic's approach to RLHF and the HH dataset.
- Christiano et al. (2017). "Deep Reinforcement Learning from Human Preferences." -- Original RLHF formulation.
- Ziegler et al. (2019). "Fine-Tuning Language Models from Human Preferences." -- Early application of RLHF to language models.

### Efficient Training and Inference

- Rajbhandari et al. (2020). "ZeRO: Memory Optimizations Toward Training Trillion Parameter Models." -- DeepSpeed ZeRO stages.
- Frantar et al. (2023). "GPTQ: Accurate Post-Training Quantization for Generative Pre-Trained Transformers." -- Weight-only quantization for inference.
- Kwon et al. (2023). "Efficient Memory Management for Large Language Model Serving with PagedAttention." -- The system behind vLLM.

### State Space Models

- Gu et al. (2022). "Efficiently Modeling Long Sequences with Structured State Spaces (S4)." -- The predecessor to Mamba.
- Gu & Dao (2023). "Mamba: Linear-Time Sequence Modeling with Selective State Spaces." -- Listed above in Must Read.
- Poli et al. (2023). "Hyena Hierarchy: Towards Larger Convolutional Language Models." -- Alternative sub-quadratic architecture.

---

## How to Read ML Papers Effectively

Reading a research paper cover-to-cover, start-to-finish, is almost never the right approach. Here is a method that works:

### First Pass: 5-10 Minutes

1. Read the **abstract**. What problem does the paper solve? What is the claimed contribution?
2. Look at the **figures and tables**. The key results are almost always in Figure 1 and Table 1. If the paper has an architecture diagram, study it.
3. Read the **conclusion**. What do the authors claim to have shown?

After this pass, you should be able to answer: What is this paper about? Should I read it more carefully?

### Second Pass: 30-60 Minutes

1. Read the **introduction** carefully. What is the motivation? What are the limitations of prior work?
2. Read the **method section**. Focus on understanding the high-level approach. Skip mathematical derivations on the first read -- focus on the intuition.
3. Study the **experiments**. What benchmarks are used? What baselines are compared? Are the improvements convincing?
4. Check the **appendix**. This is where the real implementation details live -- learning rates, data preprocessing, and the things you will need if you want to implement the paper.

### Third Pass: Implement It

You only truly understand a paper when you can implement it. This curriculum is structured so that you implement the core idea of each major paper. When you do, you will notice all the things the paper did not explain clearly -- and filling in those gaps is where the deep understanding comes from.

### General Tips

- **Read the related work section last**, not first. It is more useful after you understand the paper's contribution.
- **Keep a paper log.** For each paper, write a 3-5 sentence summary in your own words. Include: the problem, the approach, the key result, and one limitation or open question.
- **Read in clusters.** When you encounter a paper, also read the 2-3 papers it most directly builds on. Understanding the lineage makes each individual paper much clearer.
- **Do not be intimidated by math.** Most ML papers use the same core concepts: expectations, gradients, probability distributions, and matrix operations. If a derivation is opaque, try to understand what it is computing rather than following every step. The intuition matters more than the algebra.
- **Papers are not gospel.** Every paper has limitations, missing details, and sometimes errors. Read critically. If a result seems too good, check the experimental setup carefully. If a method seems needlessly complex, it might be.
