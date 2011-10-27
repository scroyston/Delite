#ifndef _BITREVERSE_H_
#define _BITREVERSE_H_

#define MASK (0x80000000)

__device__ __host__ bool BitReverse_reversed(int id) {
  return ((id & MASK) != 0);
}

__device__ __host__ int reverse(int id) {
  return id ^ MASK;
}

__device__ __host__ int internal(int id) {
  return id & ~MASK;
}

#endif
