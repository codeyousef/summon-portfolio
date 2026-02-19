---
title: "Key Libraries to Master"
section_id: ""
phase: 0
phase_title: "Appendix"
order: 2
---

# Key Libraries to Master

The modern ML ecosystem is large, but the number of libraries you actually need to be fluent in is surprisingly small. This appendix covers the core libraries you will use throughout the curriculum, with practical examples of the patterns you will encounter most often. These are not comprehensive tutorials -- they are maps of the territory, showing you the 20% of each library that covers 80% of real usage.

---

## PyTorch

PyTorch is the foundation. Every other library in this list builds on top of it. You do not need to memorize every API -- you need to be fluent in the core operations so they do not slow you down when you are focused on architecture or training logic.

### Tensor Operations You Will Use Constantly

```python
import torch

# Creating tensors
x = torch.randn(batch_size, seq_len, d_model)   # standard normal
mask = torch.ones(seq_len, seq_len).tril()        # causal mask (lower triangular)
zeros = torch.zeros_like(x)                       # same shape/device/dtype

# Reshaping -- the most common source of bugs
x = x.view(batch_size, seq_len, n_heads, head_dim)    # must be contiguous
x = x.reshape(batch_size, seq_len, n_heads, head_dim) # works even if not contiguous
x = x.permute(0, 2, 1, 3)   # [batch, heads, seq, dim] -- reorder dimensions
x = x.contiguous()           # make memory layout match logical layout

# Einstein summation -- cleaner than chains of matmul + transpose
# Batched matrix multiply: [batch, heads, seq, dim] x [batch, heads, dim, seq]
attn = torch.einsum('bhid,bhjd->bhij', queries, keys)

# Indexing and gathering
last_hidden = hidden[torch.arange(batch_size), seq_lengths - 1]  # gather last token
```

### The Module Pattern

```python
class TransformerBlock(nn.Module):
    def __init__(self, d_model, n_heads):
        super().__init__()
        self.attn = nn.MultiheadAttention(d_model, n_heads, batch_first=True)
        self.ffn = nn.Sequential(
            nn.Linear(d_model, 4 * d_model),
            nn.GELU(),
            nn.Linear(4 * d_model, d_model),
        )
        self.norm1 = nn.LayerNorm(d_model)
        self.norm2 = nn.LayerNorm(d_model)

    def forward(self, x, mask=None):
        # Pre-norm architecture (used in modern transformers)
        x = x + self.attn(self.norm1(x), self.norm1(x), self.norm1(x), attn_mask=mask)[0]
        x = x + self.ffn(self.norm2(x))
        return x
```

### Device and Dtype Management

```python
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model = MyModel().to(device)

# Mixed precision training (the standard in modern ML)
from torch.cuda.amp import autocast, GradScaler
scaler = GradScaler()

for batch in dataloader:
    batch = {k: v.to(device) for k, v in batch.items()}
    with autocast(dtype=torch.bfloat16):
        loss = model(**batch)
    scaler.scale(loss).backward()
    scaler.step(optimizer)
    scaler.update()
    optimizer.zero_grad()
```

---

## HuggingFace Transformers

The Transformers library is the Swiss Army knife of NLP. It provides pretrained models, tokenizers, and training utilities. You will use it constantly for loading models, fine-tuning, and as a reference implementation when building your own architectures.

### Loading Models and Tokenizers

```python
from transformers import AutoModelForCausalLM, AutoTokenizer

model_name = "meta-llama/Llama-2-7b-hf"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForCausalLM.from_pretrained(
    model_name,
    torch_dtype=torch.bfloat16,
    device_map="auto",          # automatically distribute across GPUs
    attn_implementation="flash_attention_2",
)

# Tokenization
text = "The transformer architecture"
tokens = tokenizer(text, return_tensors="pt", padding=True, truncation=True)
# tokens is a dict: {"input_ids": tensor, "attention_mask": tensor}

# Generation
output_ids = model.generate(
    tokens["input_ids"].to(model.device),
    max_new_tokens=100,
    temperature=0.7,
    do_sample=True,
    top_p=0.9,
)
print(tokenizer.decode(output_ids[0], skip_special_tokens=True))
```

### Using Pipelines for Quick Prototyping

```python
from transformers import pipeline

# Sentiment analysis
classifier = pipeline("sentiment-analysis")
result = classifier("This curriculum is incredibly well-structured.")
# [{'label': 'POSITIVE', 'score': 0.9998}]

# Text generation
generator = pipeline("text-generation", model="gpt2")
output = generator("The future of AI is", max_length=50, num_return_sequences=3)

# Feature extraction (get embeddings)
extractor = pipeline("feature-extraction", model="bert-base-uncased")
embeddings = extractor("Hello world")  # list of hidden states
```

### Accessing Model Internals

```python
# Get the actual architecture to study or modify
from transformers import LlamaForCausalLM, LlamaConfig

config = LlamaConfig.from_pretrained("meta-llama/Llama-2-7b-hf")
print(config.num_hidden_layers)    # 32
print(config.hidden_size)          # 4096
print(config.num_attention_heads)  # 32

# Load and inspect specific layers
model = LlamaForCausalLM.from_pretrained(model_name, torch_dtype=torch.bfloat16)
print(model.model.layers[0])  # first transformer block
print(model.model.layers[0].self_attn)  # attention module

# Hook into intermediate activations
activations = {}
def hook_fn(module, input, output):
    activations["layer_0_attn"] = output[0].detach()

model.model.layers[0].self_attn.register_forward_hook(hook_fn)
```

---

## DeepSpeed

DeepSpeed is the go-to library for distributed training. Its ZeRO (Zero Redundancy Optimizer) stages progressively shard model state across GPUs, letting you train models that would not fit on a single GPU.

### ZeRO Configuration

```python
# deepspeed_config.json -- the file you will create most often
{
    "bf16": {
        "enabled": true
    },
    "zero_optimization": {
        "stage": 2,
        "offload_optimizer": {
            "device": "none"
        },
        "allgather_bucket_size": 5e8,
        "reduce_bucket_size": 5e8,
        "overlap_comm": true,
        "contiguous_gradients": true
    },
    "gradient_accumulation_steps": 4,
    "gradient_clipping": 1.0,
    "train_batch_size": "auto",
    "train_micro_batch_size_per_gpu": "auto",
    "wall_clock_breakdown": false
}
```

**ZeRO Stage 1:** Shards optimizer states only. Reduces memory by ~4x for optimizer. Use when your model fits on one GPU but you want larger batch sizes.

**ZeRO Stage 2:** Shards optimizer states + gradients. The sweet spot for most training. Minimal communication overhead, significant memory savings.

**ZeRO Stage 3:** Shards optimizer states + gradients + model parameters. Use when the model itself does not fit on a single GPU. Higher communication cost, but enables training very large models.

### Launching DeepSpeed Training

```python
# In your training script
import deepspeed

model, optimizer, _, scheduler = deepspeed.initialize(
    model=model,
    config="deepspeed_config.json",
    model_parameters=model.parameters(),
)

for batch in dataloader:
    loss = model(batch)
    model.backward(loss)
    model.step()
```

```bash
# Launch command (4 GPUs on one machine)
deepspeed --num_gpus=4 train.py --deepspeed_config deepspeed_config.json
```

---

## Flash Attention

Flash Attention is a fused CUDA kernel that computes attention without materializing the full N x N attention matrix. It is 2-4x faster than standard attention and uses O(N) memory instead of O(N^2). If you are training any transformer, you should be using Flash Attention.

### Installation

```bash
# Requires CUDA 11.6+ and an Ampere or newer GPU (A100, RTX 3090+, H100)
pip install flash-attn --no-build-isolation
```

### Integration

```python
from flash_attn import flash_attn_func

# Input shapes: [batch, seq_len, num_heads, head_dim]
# Note: this is different from PyTorch's [batch, heads, seq, dim]!
q = q.transpose(1, 2)  # [batch, seq, heads, dim]
k = k.transpose(1, 2)
v = v.transpose(1, 2)

output = flash_attn_func(
    q, k, v,
    causal=True,        # apply causal mask
    softmax_scale=None,  # defaults to 1/sqrt(head_dim)
)
# output shape: [batch, seq_len, num_heads, head_dim]
```

```python
# With HuggingFace models -- the easiest path
model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Llama-2-7b-hf",
    attn_implementation="flash_attention_2",  # one flag, that is it
    torch_dtype=torch.bfloat16,
)
```

---

## LoRA / PEFT

Parameter-Efficient Fine-Tuning (PEFT) lets you fine-tune large models by training only a small number of additional parameters. LoRA (Low-Rank Adaptation) is the most widely used method: it adds trainable low-rank matrices to frozen attention weights.

### Setup and Training

```python
from peft import LoraConfig, get_peft_model, TaskType

# Configure LoRA
lora_config = LoraConfig(
    r=16,                          # rank of the low-rank matrices
    lora_alpha=32,                 # scaling factor (effective lr multiplier = alpha/r)
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],  # which layers to adapt
    lora_dropout=0.05,
    bias="none",
    task_type=TaskType.CAUSAL_LM,
)

# Wrap the base model
model = AutoModelForCausalLM.from_pretrained("meta-llama/Llama-2-7b-hf", torch_dtype=torch.bfloat16)
model = get_peft_model(model, lora_config)

# Check trainable parameters
model.print_trainable_parameters()
# trainable params: 4,194,304 || all params: 6,742,609,920 || trainable%: 0.0622

# Training proceeds as normal -- only LoRA weights are updated
optimizer = torch.optim.AdamW(model.parameters(), lr=2e-4)
for batch in dataloader:
    loss = model(**batch).loss
    loss.backward()
    optimizer.step()
    optimizer.zero_grad()

# Save only the LoRA weights (tiny compared to full model)
model.save_pretrained("./my-lora-adapter")  # ~17 MB for a 7B model
```

### QLoRA -- 4-Bit Quantized LoRA

QLoRA loads the base model in 4-bit precision and trains LoRA adapters on top. This lets you fine-tune a 7B model on a single GPU with 16 GB of VRAM.

```python
from transformers import BitsAndBytesConfig

quantization_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_compute_dtype=torch.bfloat16,
    bnb_4bit_quant_type="nf4",          # normalized float 4-bit
    bnb_4bit_use_double_quant=True,      # quantize the quantization constants
)

model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Llama-2-7b-hf",
    quantization_config=quantization_config,
    device_map="auto",
)
model = get_peft_model(model, lora_config)
# Now fine-tune as usual -- the 7B model uses ~5 GB of VRAM
```

---

## vLLM

vLLM is a high-throughput inference engine for LLMs. It uses PagedAttention (virtual memory for KV caches) and continuous batching to serve models efficiently. When you need to deploy a model behind an API, vLLM is the standard choice.

### Offline Batch Inference

```python
from vllm import LLM, SamplingParams

llm = LLM(
    model="meta-llama/Llama-2-7b-chat-hf",
    dtype="bfloat16",
    tensor_parallel_size=1,      # number of GPUs
    gpu_memory_utilization=0.9,  # fraction of GPU memory to use for KV cache
)

prompts = [
    "Explain transformers in one paragraph:",
    "Write a Python function to compute attention:",
    "What is the difference between LoRA and full fine-tuning?",
]

params = SamplingParams(
    temperature=0.7,
    top_p=0.9,
    max_tokens=256,
)

outputs = llm.generate(prompts, params)
for output in outputs:
    print(output.outputs[0].text)
```

### Serving with OpenAI-Compatible API

```bash
# Launch a server that mimics the OpenAI API
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Llama-2-7b-chat-hf \
    --dtype bfloat16 \
    --port 8000

# Now you can use the standard OpenAI client
```

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8000/v1", api_key="unused")
response = client.chat.completions.create(
    model="meta-llama/Llama-2-7b-chat-hf",
    messages=[{"role": "user", "content": "Hello!"}],
    temperature=0.7,
)
print(response.choices[0].message.content)
```

---

## Weights & Biases (W&B)

Experiment tracking is not optional -- it is a survival skill. When you are running dozens of experiments with different hyperparameters, architectures, and datasets, you need a systematic way to track what you tried, what worked, and what did not.

### Basic Logging

```python
import wandb

wandb.init(
    project="my-transformer",
    config={
        "learning_rate": 3e-4,
        "batch_size": 32,
        "model_size": "350M",
        "dataset": "wikitext-103",
    },
)

for step, batch in enumerate(dataloader):
    loss = train_step(batch)

    wandb.log({
        "train/loss": loss.item(),
        "train/learning_rate": scheduler.get_last_lr()[0],
        "train/grad_norm": grad_norm,
    }, step=step)

    if step % 1000 == 0:
        val_loss, val_ppl = evaluate(model, val_dataloader)
        wandb.log({
            "val/loss": val_loss,
            "val/perplexity": val_ppl,
        }, step=step)

wandb.finish()
```

### Comparing Runs

The W&B dashboard automatically lets you compare runs across hyperparameters. But the most useful feature is **sweep** -- automated hyperparameter search:

```python
sweep_config = {
    "method": "bayes",        # bayesian optimization
    "metric": {"name": "val/loss", "goal": "minimize"},
    "parameters": {
        "learning_rate": {"min": 1e-5, "max": 1e-3, "distribution": "log_uniform_values"},
        "batch_size": {"values": [8, 16, 32, 64]},
        "warmup_steps": {"min": 100, "max": 2000},
    },
}

sweep_id = wandb.sweep(sweep_config, project="my-transformer")

def train_with_sweep():
    wandb.init()
    config = wandb.config
    # Use config.learning_rate, config.batch_size, etc.
    train(lr=config.learning_rate, bs=config.batch_size)

wandb.agent(sweep_id, train_with_sweep, count=20)  # run 20 experiments
```

### Logging Artifacts

```python
# Save model checkpoints as W&B artifacts for versioning
artifact = wandb.Artifact("model-checkpoint", type="model")
artifact.add_dir("./checkpoints/step-50000/")
wandb.log_artifact(artifact)

# Log sample outputs for qualitative evaluation
table = wandb.Table(columns=["prompt", "generated_text", "step"])
for prompt in eval_prompts:
    output = generate(model, prompt)
    table.add_data(prompt, output, step)
wandb.log({"samples": table})
```

---

## Putting It All Together

A typical training script in this curriculum will use most of these libraries simultaneously. Here is what a real training loop looks like when all the pieces are connected:

```python
import torch
import wandb
from transformers import AutoTokenizer
from peft import LoraConfig, get_peft_model
from torch.cuda.amp import autocast

# Setup
wandb.init(project="capstone-lm")
tokenizer = AutoTokenizer.from_pretrained("my-base-model")
model = load_model_with_flash_attn("my-base-model")
model = get_peft_model(model, LoraConfig(r=16, target_modules=["q_proj", "v_proj"]))
optimizer = torch.optim.AdamW(model.parameters(), lr=2e-4)

# Training
for step, batch in enumerate(dataloader):
    with autocast(dtype=torch.bfloat16):
        loss = model(**batch).loss
    loss.backward()
    grad_norm = torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
    optimizer.step()
    optimizer.zero_grad()

    wandb.log({"loss": loss.item(), "grad_norm": grad_norm.item()}, step=step)

# Save and serve
model.save_pretrained("./final-adapter")
# Deploy with vLLM for inference
```

These libraries are tools, not goals. Master them well enough that they do not slow you down, then focus your attention on the ideas -- the architectures, the training dynamics, the evaluation methodology. The libraries will change over time; the understanding will not.
