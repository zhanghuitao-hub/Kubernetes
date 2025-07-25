**1.Ubuntu安装NFS**

```
sudo apt update
sudo apt sudo apt install nfs-kernel-server nfs-common
```



**2.配置NFS服务器**

2.1 创建共享目录

首先，创建一个你想要共享的目录。例如：

```
sudo mkdir /shared
sudo chown nobody:nogroup /shared
sudo chmod 777 /shared
```

2.2 编辑NFS配置文件

编辑 `/etc/exports` 文件，添加你想要共享的目录和允许访问的客户端。例如：

```
nano /etc/exports
```

添加以下内容：

```
/shared 192.168.1.0/24(rw,sync,no_subtree_check)
```

- `/shared` 是你要共享的目录。
- `192.168.1.0/24` 是允许访问的客户端IP范围。
- `rw` 表示读写权限。
- `sync` 表示同步写入。
- `no_subtree_check` 禁用子树检查。



**3.测试NFS**

```
showmount -e
```

