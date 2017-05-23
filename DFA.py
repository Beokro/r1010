import numpy as np

def calc_rms(x, scale):
    shape = (x.shape[0]//scale, scale)
    X = np.lib.stride_tricks.as_strided(x,shape=shape)
    scale_ax = np.arange(scale)
    rms = np.zeros(X.shape[0])
    for e, xcut in enumerate(X):
        coeff = np.polyfit(scale_ax, xcut, 1)
        xfit = np.polyval(coeff, scale_ax)
        rms[e] = np.sqrt(np.mean((xcut-xfit)**2))
    return rms

def dfa(data, scale_lim = [5,9], scale_dens = 0.25):
    data = np.frombuffer(data)
    cumSum = np.cumsum(data - np.mean(data))
    scales = (2**np.arange(scale_lim[0], scale_lim[1], scale_dens)).astype(np.int)
    fluct = np.zeros(len(scales))
    for index, item in enumerate(scales):
        fluct[index] = np.mean(np.sqrt(calc_rms(cumSum, item)**2))
    coeff = np.polyfit(np.log2(scales), np.log2(fluct), 1)
    return coeff[0]
