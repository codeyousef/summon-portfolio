---
title: "Capstone Projects"
section_id: ""
phase: 14
phase_title: "Phase 14: Capstone Projects (Weeks 35-40)"
order: 1
---

# Capstone Projects

You have spent thirty-four weeks building increasingly sophisticated systems from scratch. You have implemented backpropagation, attention mechanisms, full transformers, diffusion models, RLHF pipelines, state space models, and multi-agent architectures. Now it is time to put it all together.

The capstone is not a homework assignment. It is a six-week project that should challenge you, teach you things this curriculum could not anticipate, and produce something you are proud to show. Choose one of the four options below, or propose your own of equivalent scope. The goal is the same regardless: demonstrate that you can take a significant AI project from conception to completion, handling all the messy realities that textbook exercises avoid.

---

## Option A: Train a Production-Quality LLM

This is the most ambitious option. You will curate a dataset, train a language model in the 100M-1B parameter range, align it with RLHF, and deploy it behind an API. By the end, someone should be able to send a request to your endpoint and get a coherent, helpful response.

### Step 1: Curate High-Quality Training Data

The single biggest determinant of model quality is data quality. This is not a platitude -- it is an empirical observation backed by every scaling law study published since 2020. A 200M parameter model trained on clean, diverse, well-deduplicated data will outperform a 1B parameter model trained on unfiltered web scrapes.

**Data sourcing.** Start with publicly available datasets: RedPajama, The Pile, or SlimPajama. Do not attempt to scrape the web yourself -- it is a multi-month engineering project and not where you should spend your capstone time. Instead, focus on cleaning and filtering an existing corpus.

**Deduplication.** Duplicate documents in training data cause memorization, reduce effective dataset size, and inflate benchmark scores in misleading ways. Use MinHash with Locality-Sensitive Hashing (LSH) for near-duplicate detection:

```python
from datasketch import MinHash, MinHashLSH

def build_dedup_index(documents, num_perm=128, threshold=0.8):
    """Build a MinHash LSH index for near-duplicate detection."""
    lsh = MinHashLSH(threshold=threshold, num_perm=num_perm)
    minhashes = {}

    for doc_id, text in enumerate(documents):
        mh = MinHash(num_perm=num_perm)
        # Shingling: use 5-grams of characters
        for i in range(len(text) - 4):
            mh.update(text[i:i+5].encode('utf-8'))
        minhashes[doc_id] = mh
        try:
            lsh.insert(str(doc_id), mh)
        except ValueError:
            pass  # duplicate detected, skip

    # Find clusters of near-duplicates
    duplicates = set()
    for doc_id, mh in minhashes.items():
        results = lsh.query(mh)
        if len(results) > 1:
            # Keep the first, mark the rest as duplicates
            for dup_id in sorted(results)[1:]:
                duplicates.add(int(dup_id))

    return duplicates
```

**Quality filtering.** Not all text is useful for training. Apply a cascade of filters:

1. **Language detection** -- Use fastText's language ID model. Remove documents that are not in your target language with confidence below 0.7.
2. **Perplexity filtering** -- Train a small KenLM n-gram model on a known-high-quality corpus (e.g., Wikipedia). Documents with extremely high perplexity are likely garbage; extremely low perplexity indicates boilerplate or repetitive text. Keep the middle 80%.
3. **Heuristic filters** -- Remove documents with fewer than 50 words, more than 90% punctuation, or obvious quality signals like "click here to subscribe" repeated patterns.
4. **Content filters** -- Use a classifier to flag and remove toxic, NSFW, or personally identifiable content. HuggingFace has several off-the-shelf classifiers for this.

**Data mixing.** Your final training set should include diverse domains: books, code, academic papers, web text, conversation, and reference material. The ratio matters. A rough starting point: 40% web text, 20% books, 15% code, 10% academic, 10% reference, 5% conversation. Track the mix and be prepared to adjust based on downstream evaluation.

### Step 2: Train a 100M-1B Parameter Model

**Architecture decisions.** Use a decoder-only transformer with the improvements you implemented in earlier phases: RoPE (rotary positional embeddings), RMSNorm (instead of LayerNorm), SwiGLU activation, and grouped-query attention. These are well-tested choices that give you a solid architecture without introducing unnecessary risk.

**Hardware requirements.** For a 100M parameter model, a single A100 40GB is sufficient. Training should take 1-3 days on a reasonable dataset (10-50B tokens). For a 1B parameter model, you will need 4-8 A100s and training will take 1-2 weeks. Budget accordingly. Cloud costs for this range from $500 to $5,000 depending on your provider and training duration.

```python
# Model configuration for a ~350M parameter model
config = {
    "vocab_size": 32000,
    "hidden_size": 1024,
    "intermediate_size": 2816,    # SwiGLU: 2/3 * 4 * hidden_size
    "num_hidden_layers": 24,
    "num_attention_heads": 16,
    "num_key_value_heads": 4,     # GQA: 4 KV heads shared across 16 Q heads
    "max_position_embeddings": 2048,
    "rope_theta": 10000.0,
    "rms_norm_eps": 1e-5,
}

# Training configuration
training_config = {
    "batch_size_per_gpu": 8,
    "gradient_accumulation_steps": 16,  # effective batch = 128 per GPU
    "learning_rate": 3e-4,
    "min_learning_rate": 3e-5,
    "warmup_steps": 2000,
    "max_steps": 100000,
    "weight_decay": 0.1,
    "grad_clip": 1.0,
    "dtype": "bfloat16",
}
```

**Training schedule.** Use a cosine learning rate schedule with linear warmup. The warmup period should be roughly 1-2% of total training steps. Monitor loss curves, gradient norms, and learning rate throughout training. If you see loss spikes, reduce the learning rate or increase gradient clipping.

**Checkpointing.** Save checkpoints every 1,000-5,000 steps. You will lose power, run out of disk, or encounter NaN gradients at some point. Checkpoints are insurance. Also save the optimizer state so you can resume training without the learning rate reset problem.

### Step 3: Implement the Full RLHF Pipeline

This is where your capstone diverges from what most tutorials cover. You are not just calling `trl.PPOTrainer` -- you are building the pipeline yourself, using the techniques from Phase 11.

**Reward model training.** Collect or use an existing preference dataset (Anthropic's HH-RLHF or OpenAssistant). Fine-tune a copy of your base model with a scalar reward head:

```python
class RewardModel(nn.Module):
    def __init__(self, base_model):
        super().__init__()
        self.base = base_model
        self.reward_head = nn.Linear(base_model.config.hidden_size, 1)

    def forward(self, input_ids, attention_mask):
        hidden = self.base(input_ids, attention_mask).last_hidden_state
        # Use the last non-padding token's representation
        seq_lengths = attention_mask.sum(dim=1) - 1
        last_hidden = hidden[torch.arange(hidden.size(0)), seq_lengths]
        return self.reward_head(last_hidden).squeeze(-1)
```

Train with the Bradley-Terry pairwise loss: given a preferred response and a rejected response, the reward model should assign higher reward to the preferred one. This is a straightforward binary classification framing.

**PPO training loop.** The core loop: generate responses from your policy model, score them with the reward model, compute advantages, and update the policy with clipped PPO. The key hyperparameters to tune are the KL penalty coefficient (start at 0.02), the clip range (0.2 is standard), and the number of PPO epochs per batch (2-4).

Watch for reward hacking -- the model finding ways to get high reward without actually being helpful. Common symptoms: repetitive text, adversarial formatting, or degenerate outputs. The KL penalty is your main defense.

### Step 4: Deploy with API

Once you have a trained and aligned model, deploy it behind a proper inference server:

```python
# Using vLLM for efficient inference
from vllm import LLM, SamplingParams

llm = LLM(
    model="/path/to/your/model",
    tensor_parallel_size=1,  # increase for multi-GPU
    dtype="bfloat16",
    max_model_len=2048,
)

# FastAPI wrapper
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class CompletionRequest(BaseModel):
    prompt: str
    max_tokens: int = 256
    temperature: float = 0.7

@app.post("/v1/completions")
async def complete(request: CompletionRequest):
    params = SamplingParams(
        max_tokens=request.max_tokens,
        temperature=request.temperature,
    )
    outputs = llm.generate([request.prompt], params)
    return {"text": outputs[0].outputs[0].text}
```

vLLM handles PagedAttention, continuous batching, and KV-cache management for you. For production, add rate limiting, authentication, and request logging.

### Expected Timeline

| Week | Milestone |
|------|-----------|
| 1 | Data curation pipeline complete, initial dataset ready |
| 2 | Base model training launched, monitoring infrastructure in place |
| 3 | Base model training complete, begin reward model training |
| 4 | RLHF pipeline running, iterate on hyperparameters |
| 5 | Model evaluation, failure analysis, deployment |
| 6 | Documentation, demo, polish |

---

## Option B: Build a Diffusion Image Generator

Train a latent diffusion model from scratch, condition it on text using CLIP, implement classifier-free guidance, and wrap it in a web interface. By the end, someone should be able to type a prompt and get back a generated image.

### Step 1: Train a Latent Diffusion Model

Working in pixel space is prohibitively expensive. The key insight of Latent Diffusion Models (LDMs) is to train a VAE that compresses images into a lower-dimensional latent space, then run the diffusion process there.

**Train or use a pretrained VAE.** For your capstone, using a pretrained VAE (e.g., from Stable Diffusion's `stabilityai/sd-vae-ft-mse`) is acceptable. The VAE maps 512x512x3 images to 64x64x4 latents -- a 48x compression. If you want to train your own, use a KL-regularized autoencoder with a PatchGAN discriminator.

**The diffusion U-Net.** Your denoising network takes a noisy latent, a timestep, and (later) a conditioning signal, and predicts the noise. Use a U-Net with residual blocks, self-attention at 32x32 and 16x16 resolutions, and sinusoidal timestep embeddings:

```python
class DiffusionUNet(nn.Module):
    def __init__(self, in_channels=4, model_channels=256, channel_mult=(1, 2, 4, 4)):
        super().__init__()
        self.time_embed = nn.Sequential(
            SinusoidalPositionEmbeddings(model_channels),
            nn.Linear(model_channels, model_channels * 4),
            nn.SiLU(),
            nn.Linear(model_channels * 4, model_channels * 4),
        )

        # Downsampling path
        self.down_blocks = nn.ModuleList()
        ch = model_channels
        for i, mult in enumerate(channel_mult):
            out_ch = model_channels * mult
            self.down_blocks.append(ResBlock(ch, out_ch, time_channels=model_channels * 4))
            if out_ch >= model_channels * 4:
                self.down_blocks.append(SelfAttentionBlock(out_ch))
            ch = out_ch

        # Middle
        self.mid_block = ResBlock(ch, ch, time_channels=model_channels * 4)
        self.mid_attn = SelfAttentionBlock(ch)

        # Upsampling path (mirror of down)
        # ... symmetric upsampling with skip connections

    def forward(self, x, t, context=None):
        t_emb = self.time_embed(t)
        # Forward through U-Net with skip connections
        # context is injected via cross-attention (Step 2)
        pass
```

**Training the unconditional model first.** Before adding text conditioning, train on a dataset of images (LSUN Bedrooms or a curated subset of LAION) with just the standard DDPM objective: predict the noise added at each timestep. Train for at least 100K steps before evaluating. Use EMA (exponential moving average) of model weights for inference -- this dramatically improves sample quality.

### Step 2: Add Text Conditioning with CLIP

Use a frozen CLIP text encoder (OpenAI's `clip-vit-large-patch14` is the standard choice) to produce text embeddings. Inject these into the U-Net via cross-attention:

```python
class CrossAttentionBlock(nn.Module):
    def __init__(self, channels, context_dim=768):  # 768 for CLIP ViT-L
        super().__init__()
        self.norm = nn.GroupNorm(32, channels)
        self.q = nn.Linear(channels, channels)
        self.k = nn.Linear(context_dim, channels)
        self.v = nn.Linear(context_dim, channels)
        self.out_proj = nn.Linear(channels, channels)

    def forward(self, x, context):
        b, c, h, w = x.shape
        x_flat = self.norm(x).reshape(b, c, h * w).permute(0, 2, 1)

        q = self.q(x_flat)
        k = self.k(context)
        v = self.v(context)

        # Standard scaled dot-product attention
        attn = torch.softmax(q @ k.transpose(-2, -1) / (c ** 0.5), dim=-1)
        out = (attn @ v).permute(0, 2, 1).reshape(b, c, h, w)
        return x + self.out_proj(out.reshape(b, c, -1)).reshape(b, c, h, w)
```

Replace the self-attention blocks in your U-Net with blocks that do both self-attention and cross-attention. The context tensor is the sequence of CLIP text embeddings (shape `[batch, seq_len, 768]`).

### Step 3: Classifier-Free Guidance

Classifier-free guidance (CFG) is the technique that makes text-conditional diffusion models actually follow prompts. During training, randomly drop the text conditioning (replace with a null embedding) some fraction of the time -- 10-20% is standard. At inference time, run two forward passes per timestep: one conditioned, one unconditioned. Then steer toward the conditioned prediction:

```python
def guided_denoise(model, x_t, t, text_embedding, null_embedding, guidance_scale=7.5):
    """One step of classifier-free guided denoising."""
    # Unconditional prediction
    noise_uncond = model(x_t, t, context=null_embedding)
    # Conditional prediction
    noise_cond = model(x_t, t, context=text_embedding)
    # Guided prediction: steer away from unconditional
    return noise_uncond + guidance_scale * (noise_cond - noise_uncond)
```

The `guidance_scale` controls how strongly the model follows the prompt. Values between 5 and 15 work well; higher values produce more prompt-adherent but sometimes oversaturated images. This is the single most impactful hyperparameter at inference time.

### Step 4: Create a Web Demo

Wrap your model in a Gradio interface so anyone can try it:

```python
import gradio as gr
from PIL import Image

def generate_image(prompt, num_steps=50, guidance_scale=7.5, seed=42):
    torch.manual_seed(seed)
    text_emb = clip_encode(prompt)
    null_emb = clip_encode("")

    # Start from pure noise in latent space
    latents = torch.randn(1, 4, 64, 64, device=device)

    # DDPM/DDIM sampling loop
    for t in reversed(range(num_steps)):
        latents = denoise_step(latents, t, text_emb, null_emb, guidance_scale)

    # Decode latents to pixel space
    image = vae.decode(latents)
    return to_pil(image)

demo = gr.Interface(
    fn=generate_image,
    inputs=[
        gr.Textbox(label="Prompt"),
        gr.Slider(10, 100, value=50, label="Steps"),
        gr.Slider(1.0, 20.0, value=7.5, label="Guidance Scale"),
        gr.Number(value=42, label="Seed"),
    ],
    outputs=gr.Image(label="Generated Image"),
    title="Capstone Diffusion Model",
)
demo.launch()
```

### Tips From Experience

- Train the unconditional model first and verify it produces reasonable samples before adding text conditioning. Debugging two things at once is a recipe for frustration.
- FID (Frechet Inception Distance) is the standard metric, but also look at your samples. Metrics can be misleading -- especially on small datasets.
- If your model produces muddy or blurry images, the VAE reconstruction quality is often the bottleneck. Check VAE decode quality on real images first.
- DDIM sampling lets you reduce the number of steps from 1000 to 50 with minimal quality loss. Implement it for your demo.

---

## Option C: Research Replication

Replicating a published paper from scratch is one of the most effective ways to develop research skills. You will learn to read papers critically, handle ambiguity, debug subtle implementation details, and communicate your findings.

### How to Pick a Good Paper

Not all papers are equally suitable for replication. Look for:

1. **Clear methodology.** The paper should describe its method in enough detail that you can implement it without access to the authors' code. Papers with extensive supplementary material and appendices are gold.
2. **Achievable compute.** You need to be able to reproduce the core result -- not necessarily at the full scale, but at a scale that demonstrates the key contribution. A paper that requires 1,000 A100-hours for its smallest experiment is a poor choice unless you have serious compute access.
3. **Available data.** The training data and evaluation benchmarks must be publicly available. Papers using proprietary datasets are dead ends.
4. **Verifiable results.** The paper should report specific numbers on standard benchmarks that you can compare against.

**Good candidates for this capstone:**
- A recent efficient attention mechanism (test on a standard language modeling or image classification task)
- A novel training technique (new optimizer, new normalization, new regularization)
- A data augmentation or curriculum learning strategy
- A parameter-efficient fine-tuning method

**Avoid for a six-week capstone:**
- Papers that require training from scratch on ImageNet-scale or larger
- Papers whose key result depends on a proprietary model or dataset
- Papers where the main contribution is a dataset rather than a method

### Common Challenges

**Missing details.** Every paper omits something. Learning rate schedules, weight initialization schemes, data preprocessing steps, and early stopping criteria are frequently under-specified. When you encounter ambiguity:
1. Check the appendix and supplementary material.
2. Look for an official or widely-used implementation on GitHub.
3. Try the most standard choice first (e.g., Xavier initialization, AdamW, cosine schedule).
4. If multiple reasonable interpretations exist, try them all and document the differences.

**Compute requirements.** The results in the paper were probably produced with more compute than you have. This is normal. Scale down systematically: use a smaller model, a smaller dataset, or fewer training steps. The key contribution should still be visible at smaller scale, even if the absolute numbers are lower.

**Numerical precision.** You will almost certainly not match the paper's numbers exactly. Differences of 1-2% on benchmarks are normal and expected due to random seeds, framework differences, and undocumented implementation details. If your numbers are more than 5% off, something is wrong and you need to investigate.

### How to Document Your Work

Keep a research log from day one. Every experiment should have:
- The exact configuration (hyperparameters, data, model size)
- The result (loss curves, final metrics, wall-clock time)
- What you learned or what changed from the previous experiment

Write your final report as if it were a short paper. Include:
1. **Introduction** -- what you replicated and why
2. **Method** -- the paper's method, in your own words
3. **Implementation details** -- everything the paper did not tell you that you had to figure out
4. **Results** -- your numbers compared to the paper's, with analysis of discrepancies
5. **Ablations** -- at least one experiment that tests a specific design choice
6. **Lessons learned** -- what you would do differently

### Publishing Your Findings

Replication studies are valuable contributions to the community. Consider:
- **Blog post** -- the most accessible format. Include code, figures, and honest discussion of difficulties.
- **GitHub repository** -- clean, documented code with a README that explains how to reproduce your results.
- **Papers With Code** -- if your replication is thorough, add it as a community implementation.
- **OpenReview or arXiv** -- for particularly thorough replications that add new insights.

---

## Option D: Novel Architecture

Design, implement, and evaluate a new architectural component or training procedure. This is the most research-oriented option and the most open-ended. You are not expected to beat state-of-the-art -- you are expected to run rigorous experiments, analyze results honestly, and draw well-supported conclusions.

### Designing Experiments with Proper Baselines

Every experiment needs a baseline. The baseline should be:
- **Well-understood** -- use a standard architecture (transformer, MLP, etc.)
- **Fairly tuned** -- give the baseline the same hyperparameter tuning budget you give your method
- **Identically evaluated** -- same data, same metrics, same evaluation procedure

A common mistake is to compare a tuned novel method against an untuned baseline. This is not science. If your method benefits from a particular learning rate schedule, check whether the baseline also benefits from that schedule.

```python
# Experiment configuration pattern
experiments = {
    "baseline_transformer": {
        "model": TransformerLM,
        "config": {"d_model": 512, "n_layers": 6, "n_heads": 8},
        "lr_options": [1e-4, 3e-4, 1e-3],
        "seeds": [42, 137, 256],
    },
    "your_method": {
        "model": YourNovelLM,
        "config": {"d_model": 512, "n_layers": 6, "n_heads": 8, "your_param": True},
        "lr_options": [1e-4, 3e-4, 1e-3],
        "seeds": [42, 137, 256],
    },
}

# Run all combinations and report mean +/- std across seeds
for name, exp in experiments.items():
    for lr in exp["lr_options"]:
        for seed in exp["seeds"]:
            result = train_and_evaluate(exp["model"], exp["config"], lr, seed)
            log_result(name, lr, seed, result)
```

### Ablation Study Methodology

An ablation study removes or modifies one component at a time to understand its contribution. If your method has three novel components (A, B, C), you need:

| Experiment | A | B | C | Purpose |
|-----------|---|---|---|---------|
| Full method | Y | Y | Y | Your complete approach |
| No A | N | Y | Y | How much does A contribute? |
| No B | Y | N | Y | How much does B contribute? |
| No C | Y | Y | N | How much does C contribute? |
| Baseline | N | N | N | The starting point |

This matrix tells you which components are load-bearing and which are optional. It is one of the most convincing things you can include in a technical report.

### Benchmarking and Evaluation

Choose benchmarks appropriate to your method:
- **Language modeling** -- WikiText-103 perplexity, LAMBADA accuracy
- **Classification** -- CIFAR-10/100, ImageNet-1k (if you have compute)
- **Efficiency** -- throughput (tokens/sec), memory usage, time to convergence

Report wall-clock time alongside accuracy. A method that achieves the same accuracy in half the training time is a genuine contribution, even if it does not improve the final number.

**Statistical significance.** Run every experiment with at least three different random seeds. Report mean and standard deviation. A 0.3% improvement with overlapping error bars is not a result -- it is noise.

### Writing a Technical Report

Structure your report as a short conference paper:

1. **Abstract** (150 words) -- what you did, what you found
2. **Introduction** (1 page) -- the problem, why it matters, your approach, your findings
3. **Related Work** (0.5-1 page) -- what others have tried, how your approach differs
4. **Method** (1-2 pages) -- your architecture/technique in full detail, with diagrams
5. **Experiments** (2-3 pages) -- setup, baselines, main results, ablations
6. **Analysis** (1 page) -- why does your method work (or not work)? What are the limitations?
7. **Conclusion** (0.5 page) -- summary, future directions

Be honest about negative results. If your method does not work as expected, understanding and documenting why is just as valuable as a positive result. Reviewers and readers respect intellectual honesty far more than inflated claims.

---

## General Capstone Advice

**Start with a plan, but expect it to change.** Write a one-page project proposal in week one. Revisit it every week and adjust. No capstone project ever goes exactly as planned, and that is fine.

**Track everything with W&B or MLflow.** You will run dozens of experiments. Without systematic tracking, you will lose track of which configuration produced which result. Set up experiment tracking on day one, not day fifteen.

**Ask for feedback early.** If you are working with a mentor, study group, or online community, share your progress after week two -- not after week five. Early feedback prevents wasted effort.

**The demo matters.** Whatever option you choose, build something someone else can interact with. A live demo, a Colab notebook, or a well-documented GitHub repo with one-command reproduction. The ability to show your work -- not just describe it -- is what separates a capstone from an exercise.

**Write as you go.** Do not leave documentation to the last week. Write your methodology section while implementing, your results section after each major experiment, and your introduction last. Writing clarifies thinking, and thinking clarifies implementation.
