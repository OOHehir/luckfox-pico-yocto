/*
 * RKNN NPU Demo for Luckfox Pico Ultra W (RV1106)
 *
 * Loads an RKNN model, runs inference using the zero-copy DMA
 * memory API (rknn_create_mem + rknn_set_io_mem), and prints
 * timing results.
 *
 * This uses the "native" tensor format (NC1HWC2 for outputs)
 * which is required by the v1.6.0 mini runtime on RV1106.
 *
 * Usage: rknn_demo [model_path]
 *   Default model: /usr/share/rknn-demo/detection.rknn
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <stdint.h>

#include "rknn/rknn_api.h"

#define DEFAULT_MODEL "/usr/share/rknn-demo/detection.rknn"
#define LOOP_COUNT 10
#define MAX_OUTPUTS 8

static int64_t time_us(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000000LL + tv.tv_usec;
}

static unsigned char *load_file(const char *path, int *size)
{
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        fprintf(stderr, "Error: Cannot open %s\n", path);
        return NULL;
    }
    fseek(fp, 0, SEEK_END);
    *size = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    unsigned char *buf = malloc(*size);
    if (!buf) {
        fclose(fp);
        return NULL;
    }
    fread(buf, 1, *size, fp);
    fclose(fp);
    return buf;
}

int main(int argc, char *argv[])
{
    const char *model_path = (argc > 1) ? argv[1] : DEFAULT_MODEL;
    rknn_context ctx = 0;
    int ret;

    printf("=== RKNN NPU Demo ===\n");
    printf("Model: %s\n\n", model_path);

    /* Load model file */
    int model_size = 0;
    unsigned char *model_data = load_file(model_path, &model_size);
    if (!model_data)
        return -1;
    printf("Model loaded: %d bytes (%.1f KB)\n", model_size, model_size / 1024.0);

    /* Initialize RKNN context */
    int64_t t0 = time_us();
    ret = rknn_init(&ctx, model_data, model_size, 0, NULL);
    int64_t t1 = time_us();
    free(model_data);

    if (ret < 0) {
        fprintf(stderr, "Error: rknn_init failed (%d)\n", ret);
        fprintf(stderr, "\nNote: model must be compiled with toolkit v1.x for runtime v1.6.0\n");
        return -1;
    }
    printf("NPU initialized in %.1f ms\n\n", (t1 - t0) / 1000.0);

    /* Query SDK version */
    rknn_sdk_version sdk_ver;
    ret = rknn_query(ctx, RKNN_QUERY_SDK_VERSION, &sdk_ver, sizeof(sdk_ver));
    if (ret == 0) {
        printf("RKNN API: %s\n", sdk_ver.api_version);
        printf("Driver:   %s\n\n", sdk_ver.drv_version);
    }

    /* Query input/output count */
    rknn_input_output_num io_num;
    ret = rknn_query(ctx, RKNN_QUERY_IN_OUT_NUM, &io_num, sizeof(io_num));
    if (ret < 0) {
        fprintf(stderr, "Error: query io_num failed\n");
        goto cleanup;
    }
    printf("Inputs: %d, Outputs: %d\n", io_num.n_input, io_num.n_output);

    /* Query native input attributes and allocate DMA memory */
    rknn_tensor_attr in_attr;
    memset(&in_attr, 0, sizeof(in_attr));
    in_attr.index = 0;
    ret = rknn_query(ctx, RKNN_QUERY_NATIVE_INPUT_ATTR, &in_attr, sizeof(in_attr));
    if (ret < 0) {
        fprintf(stderr, "Error: query native input attr failed\n");
        goto cleanup;
    }
    printf("Input[0]: %s [%d,%d,%d,%d] fmt=%d type=%d size=%d\n",
           in_attr.name,
           in_attr.dims[0], in_attr.dims[1], in_attr.dims[2], in_attr.dims[3],
           in_attr.fmt, in_attr.type, in_attr.size);

    rknn_tensor_mem *in_mem = rknn_create_mem(ctx, in_attr.size);
    if (!in_mem) {
        fprintf(stderr, "Error: failed to create input DMA memory\n");
        goto cleanup;
    }
    /* Fill with gray (128) — in real use, copy actual image data here */
    memset(in_mem->virt_addr, 128, in_attr.size);

    ret = rknn_set_io_mem(ctx, in_mem, &in_attr);
    if (ret < 0) {
        fprintf(stderr, "Error: set_io_mem(input) failed (%d)\n", ret);
        goto cleanup;
    }

    /* Query native output attributes and allocate DMA memory */
    rknn_tensor_mem *out_mems[MAX_OUTPUTS] = {0};
    rknn_tensor_attr out_attrs[MAX_OUTPUTS];
    int n_out = io_num.n_output < MAX_OUTPUTS ? io_num.n_output : MAX_OUTPUTS;

    for (int i = 0; i < n_out; i++) {
        memset(&out_attrs[i], 0, sizeof(out_attrs[i]));
        out_attrs[i].index = i;
        ret = rknn_query(ctx, RKNN_QUERY_NATIVE_OUTPUT_ATTR, &out_attrs[i], sizeof(out_attrs[i]));
        if (ret < 0) {
            fprintf(stderr, "Error: query native output[%d] attr failed\n", i);
            goto cleanup;
        }
        printf("Output[%d]: %s [%d,%d,%d,%d] fmt=%d type=%d size=%d\n", i,
               out_attrs[i].name,
               out_attrs[i].dims[0], out_attrs[i].dims[1],
               out_attrs[i].dims[2], out_attrs[i].dims[3],
               out_attrs[i].fmt, out_attrs[i].type, out_attrs[i].size);

        out_mems[i] = rknn_create_mem(ctx, out_attrs[i].size);
        if (!out_mems[i]) {
            fprintf(stderr, "Error: failed to create output[%d] DMA memory\n", i);
            goto cleanup;
        }
        ret = rknn_set_io_mem(ctx, out_mems[i], &out_attrs[i]);
        if (ret < 0) {
            fprintf(stderr, "Error: set_io_mem(output[%d]) failed (%d)\n", i, ret);
            goto cleanup;
        }
    }

    /* Warmup run */
    printf("\nWarmup...\n");
    ret = rknn_run(ctx, NULL);
    if (ret < 0) {
        fprintf(stderr, "Error: warmup rknn_run failed (%d)\n", ret);
        goto cleanup;
    }

    /* Benchmark loop */
    printf("Running %d inferences...\n", LOOP_COUNT);
    int64_t total_us = 0;
    for (int i = 0; i < LOOP_COUNT; i++) {
        int64_t start = time_us();
        ret = rknn_run(ctx, NULL);
        int64_t end = time_us();
        if (ret < 0) {
            fprintf(stderr, "Error: rknn_run failed (%d) on iteration %d\n", ret, i);
            break;
        }
        total_us += (end - start);
    }

    if (ret >= 0) {
        double avg_ms = (double)total_us / LOOP_COUNT / 1000.0;
        printf("\nAverage inference time: %.2f ms (%.1f FPS)\n", avg_ms, 1000.0 / avg_ms);
        printf("\n=== NPU test PASSED ===\n");
    }

cleanup:
    rknn_destroy(ctx);
    return (ret < 0) ? -1 : 0;
}
