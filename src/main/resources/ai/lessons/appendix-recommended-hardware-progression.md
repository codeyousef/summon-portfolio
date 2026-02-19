---
title: "Recommended Hardware Progression"
section_id: ""
phase: 0
phase_title: "Appendix"
order: 1
---

# Recommended Hardware Progression

One of the most common questions from people starting this curriculum is "what hardware do I need?" The honest answer is that it depends on where you are in the curriculum, and the requirements change significantly as you progress. This appendix lays out a practical progression, including what you can accomplish at each tier, where the limitations are, and how to manage costs.

The guiding principle: **start cheap and scale only when you actually hit a wall.** Many people buy or rent expensive hardware before they need it, then spend more time configuring CUDA drivers than learning backpropagation. Do not be that person.

---

## Phase 1-3: CPU or Free-Tier GPU

**What you are doing:** Implementing fundamentals from scratch -- NumPy operations, a manual autograd engine, basic neural networks on small datasets (MNIST, CIFAR-10), convolutional networks, basic RNNs.

**What you need:** A laptop. Seriously. The datasets are small (CIFAR-10 is 170 MB), the models are small (under 1M parameters), and the entire point is to understand what happens inside the computation, not to train as fast as possible. Training a small CNN on CIFAR-10 on a modern CPU takes 10-30 minutes, which is perfectly fine when you are debugging your gradient computation.

**Google Colab free tier** gives you a Tesla T4 (16 GB VRAM) for limited sessions. This is more than enough for Phases 1-3 and provides a useful introduction to GPU workflows without any cost. The limitations -- session timeouts, limited disk, sporadic availability -- are irrelevant at this stage because your experiments are short.

**What you can do:**
- Train models with up to ~10M parameters
- Work with datasets that fit in RAM (up to ~8 GB)
- Run experiments that complete in under an hour
- Implement and debug autograd, CNNs, basic transformers on toy tasks

**What you cannot do (and do not need to yet):**
- Train on ImageNet or larger datasets
- Train large transformers or language models
- Run experiments that require days of compute

**Practical tip:** If your laptop does not have an NVIDIA GPU, install PyTorch CPU-only. Do not waste time fighting CUDA installations at this stage. You will move to GPU soon enough, and CPU training forces you to write efficient code -- a habit that pays dividends later.

---

## Phase 4-6: Single GPU

**What you are doing:** Training full transformers, working with larger datasets (WikiText, subsets of The Pile), implementing attention mechanisms, building your first language models, training diffusion models on 64x64 or 128x128 images.

**When to upgrade:** When your experiments take more than 2-3 hours on CPU/Colab and you are running multiple experiments per day. If you are spending more time waiting for training than coding, it is time.

### Option 1: RTX 4090 (24 GB VRAM) -- Buy or Rent

The RTX 4090 is the best consumer GPU for ML as of 2024-2025. At 24 GB VRAM, it can train models up to roughly 1-2B parameters (with mixed precision and gradient checkpointing) and handles most single-GPU workloads in this curriculum.

**Advantages:** High memory bandwidth, excellent FP16/BF16 throughput, widely available, strong community support.

**Limitations:** 24 GB is not enough for training models larger than ~2B parameters even with optimization. No NVLink for multi-GPU scaling (consumer cards use PCIe).

**Cost:** ~$1,600 USD retail. If you plan to do ML for more than 6 months, buying is almost always cheaper than renting.

### Option 2: A100 40GB -- Cloud Rental

The A100 is the workhorse of production ML. 40 GB of HBM2e with 1.6 TB/s bandwidth. Superior to the 4090 for large batch training and any workload that needs more than 24 GB of VRAM.

**Where to rent:**

| Provider | A100 40GB Price | Notes |
|----------|----------------|-------|
| Lambda Cloud | ~$1.10/hr | Simple, reliable, good for short jobs |
| RunPod | ~$1.00-1.50/hr | Spot instances available, community cloud cheaper |
| Vast.ai | ~$0.70-1.20/hr | Cheapest, but variable reliability, peer-hosted |
| Google Colab Pro+ | $50/month | A100 access, but session limits still apply |
| AWS (p4d.24xlarge) | ~$32/hr (8xA100) | Only worth it if you need the full node |

**Recommendation for this stage:** If you already own a gaming PC with an RTX 3090 or 4090, use that. If not, rent A100s on RunPod or Vast.ai for the specific experiments that need them. Most Phase 4-6 work fits on a 4090.

### Estimating GPU Memory Requirements

A rough formula for transformer training memory (mixed precision, no activation checkpointing):

```
Memory (GB) ≈ 4 * num_parameters_billions * (model + optimizer + gradients + activations)
```

More precisely:
- **Model weights** in FP16: `2 bytes * num_params`
- **Optimizer states** (AdamW): `8 bytes * num_params` (FP32 copy + momentum + variance)
- **Gradients** in FP16: `2 bytes * num_params`
- **Activations**: depends on batch size and sequence length; roughly `2 * batch_size * seq_len * hidden_dim * num_layers` bytes

```python
def estimate_training_memory_gb(
    num_params_millions,
    batch_size,
    seq_len,
    hidden_dim,
    num_layers,
):
    """Rough estimate of GPU memory needed for transformer training."""
    num_params = num_params_millions * 1e6

    # Weights (FP16) + Optimizer (FP32 master + momentum + variance) + Gradients (FP16)
    param_memory = num_params * (2 + 8 + 2)  # bytes

    # Activations (rough estimate, assumes no checkpointing)
    activation_memory = 2 * batch_size * seq_len * hidden_dim * num_layers * 2  # bytes

    total_bytes = param_memory + activation_memory
    total_gb = total_bytes / (1024 ** 3)

    return total_gb

# Example: 350M param model, batch 8, seq 2048, hidden 1024, 24 layers
print(estimate_training_memory_gb(350, 8, 2048, 1024, 24))
# Approximately 12-15 GB -- fits on a 4090 or A100
```

**Batch size tuning:** Start with the largest batch size that fits in memory, then halve it if you get OOM errors. Use gradient accumulation to maintain effective batch size:

```python
# If batch_size=32 causes OOM, use batch_size=8 with 4 accumulation steps
accumulation_steps = 4
optimizer.zero_grad()
for i, batch in enumerate(dataloader):  # dataloader has batch_size=8
    loss = model(batch) / accumulation_steps
    loss.backward()
    if (i + 1) % accumulation_steps == 0:
        optimizer.step()
        optimizer.zero_grad()
```

---

## Phase 7-10: Multi-GPU or Cloud

**What you are doing:** Training larger language models (1B+), multi-GPU distributed training, LoRA and QLoRA fine-tuning of large models, RLHF pipelines, diffusion models at 256x256+.

**When to upgrade:** When your models no longer fit on a single GPU, or when training time on a single GPU exceeds a week. At this point you need either multi-GPU machines or cloud instances with multiple GPUs.

### Cloud Cost Estimates

| Workload | Hardware | Est. Time | Est. Cost |
|----------|----------|-----------|-----------|
| Fine-tune 7B model (LoRA) | 1x A100 80GB | 4-12 hours | $5-15 |
| Fine-tune 7B model (full) | 4x A100 80GB | 2-5 days | $200-500 |
| Train 1B model from scratch | 4x A100 80GB | 5-14 days | $500-1,500 |
| RLHF pipeline (7B policy) | 4x A100 80GB | 3-7 days | $300-700 |
| Diffusion model (256x256) | 2x A100 40GB | 3-7 days | $150-350 |

### When to Scale

Scaling to more GPUs is not always the answer. Before renting a multi-GPU instance, verify that:
1. **Your code is actually GPU-bound.** Run `nvidia-smi` during training. If GPU utilization is below 80%, your bottleneck is data loading, CPU preprocessing, or I/O -- adding more GPUs will not help.
2. **You have optimized single-GPU performance first.** Mixed precision, gradient checkpointing, and efficient data loading should be in place before scaling.
3. **The experiment justifies the cost.** If you are still iterating on hyperparameters, do it at small scale first, then scale up for the final run.

### Multi-GPU Setup

DeepSpeed ZeRO-2 is the simplest way to scale to multiple GPUs. It shards optimizer states and gradients across GPUs, roughly tripling the model size you can train on a given amount of total VRAM:

```python
# deepspeed_config.json
{
    "bf16": {"enabled": true},
    "zero_optimization": {
        "stage": 2,
        "offload_optimizer": {"device": "none"},
        "allgather_bucket_size": 5e8,
        "reduce_bucket_size": 5e8
    },
    "train_batch_size": 32,
    "train_micro_batch_size_per_gpu": 4,
    "gradient_accumulation_steps": 4
}
```

---

## Phase 11+: As Needed

**What you are doing:** Capstone projects, research experiments, specialized training runs.

**At this point, you know what you need.** The hardware requirements depend entirely on your chosen project. The key skill to develop in this phase is cost management -- knowing when to spend compute and when to save it.

### Tips for Managing Cloud Costs

**Use spot/preemptible instances.** Most cloud providers offer 60-80% discounts for instances that can be interrupted. For training runs with checkpointing (which all your training runs should have), spot instances are almost always the right choice. The interruption rate is low enough that you will rarely lose more than a few minutes of work.

**Monitor actively.** Set up billing alerts. It is embarrassingly easy to leave an instance running over a weekend and spend $500 on idle compute. Shut down instances the moment your job finishes. Write scripts that automatically shut down the machine when training completes:

```python
import subprocess
import sys

def shutdown_on_complete():
    """Call this at the end of your training script."""
    print("Training complete. Shutting down instance in 60 seconds...")
    print("Cancel with Ctrl+C if you want to keep the instance running.")
    import time
    time.sleep(60)
    subprocess.run(["sudo", "shutdown", "-h", "now"])

# At the end of train.py:
if __name__ == "__main__":
    train()
    save_final_checkpoint()
    upload_results_to_cloud_storage()
    shutdown_on_complete()
```

**Develop locally, train remotely.** Write and debug your code on your laptop or a cheap instance. Only spin up expensive GPU instances when you have a tested training script ready to run. A common workflow:

1. Develop on laptop (CPU, tiny dataset, 10 iterations)
2. Test on single cheap GPU (full dataset, 100 iterations, verify loss decreases)
3. Launch full training on expensive multi-GPU instance (full run, checkpointing, auto-shutdown)

**Use cloud storage, not local disk.** Upload checkpoints and results to S3/GCS during training. If your spot instance gets preempted, you do not lose anything. The cost of cloud storage is negligible compared to GPU time.

**Keep a cost spreadsheet.** Track every cloud expenditure. Knowing that your capstone project cost $847 in compute is useful information -- both for planning future projects and for appreciating just how much compute modern AI research consumes.
