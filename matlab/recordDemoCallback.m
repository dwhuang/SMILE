if any(strcmp('rgbVision', fieldnames(sensor))) && sensor.demoCue
    imwrite(sensor.rgbVision, [aux.path, '/demoImages/', num2str(sn), '.png']);
    sn = sn + 1;
end
