# Spring Cloud Kubernetes Leader Election Example
This project demonstrates leader election in a Kubernetes cluster using Kubernetes Leases and the [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client).

## Purpose and motivations
Currently, the microservices architecture is fully extended and is one of the most widely used by all companies to implement their solutions, although older ones such as the monolithic are still present for certain cases where they are more appropriate and other, newer ones, such as serverless are beginning to be more recurrent. The most natural thing to do is to deploy these microservices in container-based environments, such as Kubernetes, which facilitate the deployment and maintenance of our applications. A very important thing to keep in mind when developing our microservices is high availability, i.e. ensuring that the application can process a high number of requests without crashing or failing. In Kubernetes, to solve this, replicas are used; that is, several instances of the same microservice are created and requests are redirected to one replica or another, depending on the load and the configuration that has been made. 

For example, nothing should be stored in the microservice's own memory, because if the same user makes two requests, Kubernetes cannot ensure that both requests are processed in the same replica. Another problem, and the one explained in this example, is when our application needs to process scheduled tasks from time to time. By having several replicas, using the classic techniques, each replica would execute the same task at the same time, when, in fact, we require it to be executed only once. For these cases, we usually use leader selection solutions, such as Zookeeper, or we implement it manually using a key-value database, such as Redis.

However, in many cases, it is quite advisable to take advantage of what the existing infrastructure offers, as setting up solutions like Zookeeper can lead to additional costs for the infrastructure, or implementing custom solutions, such as using Redis, can complicate the development process. 

This project shows how to implement leader election in Spring Boot applications using Kubernetes infrastructure, specifically using the Fabric8 Kubernetes client to manage Lease-based leader election.

## How it works
This example uses Kubernetes Leases for leader election through the Fabric8 Kubernetes client. Leases are Kubernetes coordination objects specifically designed for leader election scenarios. The application uses the `io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector` class to participate in the leader election process.

The Lease resource is pre-configured in [deployment.yml](deployment.yml) and created on apply. This ensures exact control over the Lease's initial state (name, namespace, duration). The app detects and uses this existing Lease directly—no dynamic creation needed.

When multiple replicas start, they compete to acquire the lease by updating its `holderIdentity` field. Only one instance succeeds and becomes the leader. The leader continuously renews the lease (updating `renewTime`) within the renew deadline. If the leader fails to renew, followers can acquire leadership.

The leader election process is configured with:
- **Lease Name**: `leader-example` (pre-defined in deployment.yml; overridable via LEASE_NAME)
- **Namespace**: `default` (pre-defined; overridable via LEASE_NAMESPACE if needed)
- **Lease Duration**: 30 seconds (pre-set in Lease spec; app respects it)
- **Renew Deadline**: 20 seconds (app-controlled via LEASE_RENEW_DEADLINE_SECONDS)
- **Retry Period**: 5000 milliseconds (app-controlled via LEASE_RETRY_PERIOD_MILLIS)

These values can be configured via environment variables in [deployment.yml](deployment.yml) or the `application.yml` file. The pre-configured Lease matches the defaults for consistency.

## Requirements

### Build requirements
This project is based on SpringBoot, so it requires a JDK installed on system where will be builded; t
the required version is 21. Gradle is not needed to be installed, because this project has a wrapper to build project without installation.

### Testing requirements
This example uses a Kubernetes feature, and is designed to be tested with Minikube, which is a tool that allows you to run a local Kubernetes cluster on a virtual machine, making it easier to develop and test Kubernetes applications locally. So, you need to install it before to test the application. This is the official page with instructions to install Minikube in all platforms: [how to install minikube](https://minikube.sigs.k8s.io/docs/start/?arch=%2Fmacos%2Farm64%2Fstable%2Fbinary+download).

To install Minikube, also Docker is required, because Minikube uses it to deploy the cluster. Also, to deploy this application on Minikube, is required kubectl tool to interact with cluster. This is the official page of Kubernetes with instructions to install kubectl on different operative systems: [how to install kubectl](https://kubernetes.io/docs/tasks/tools/).

## Project structure
This repository has a Spring Boot project structure, with dependency management based on Gradle. The files and folders in project are:

* gradle: Folder with a Gradle wrapper to build project without Gradle installed on system
* src: Folder with all Java project code and resources
  * `LeaseLeaderManager.java`: Manages the Lease-based leader election process using Fabric8's LeaderElector
  * `LeaderEvents.java`: Tracks leadership state (leader or not) for the application
  * `ScheduledTask.java`: Example scheduled task that only runs on the leader instance
* .gitignore: Git ignore file to don't track some files
* build.gradle: Gradle project configuration, uses `io.fabric8:kubernetes-client` for leader election
* deploy.bat: Windows terminal script to automatize build and deploy on Minikube
* deploy.sh: Bash script to automatize build and deploy on Minikube
* deployment.yml: YAML file with all Kubernetes objects including RBAC for Lease access
* gradlew: Script for Linux and MacOS to run Gradle wrapper
* gradlew.bat: Script for Windows to run Gradle wrapper
* settings.gradle: Gradle project's settings file. In this case, only contains project name

## How to test application
The application testing is automatized in deploy.sh and deploy.bat files, you can use first one if you're using Linux or MacOS system, or second one if you're using Windows. These scripts has this steps:

1. First, set some environment variables to use Minikube's Docker environment instead of local Docker environment. This is needed because our application must be visible by Minikube system, so, the image of our application must be registered inside Minikube local registry.
2. Then, builds the SpringBoot application and generates a Docker image using SpringBoot's custom gradle task. This task creates an image and push it to local registry automatically.
3. Applies the deployment which creates instances of our application. Also generates another objects that the application needs, like roles, role bindings, service accounts,...

If script runs correctly, you can view logs of application instances. To make this easy, run this command in a terminal: `minikube dashboard`, which launches a web application to show Minikube's information and control it. In this dashboard you can view pods deployed and show logs.

To verify the example works correctly:
1. Apply [deployment.yml](deployment.yml): The Lease will be created first in the default namespace.
2. Only one of the two instances should show the message "I'm currently the leader" in the log.
3. The other instance should show "I'm not the leader".
4. If you delete the leader pod, the other instance will acquire leadership and start showing "I'm currently the leader" (within ~20-30 seconds based on timings).
5. Verify the Lease: `kubectl get lease leader-example -n default -o yaml`. Check `holderIdentity` (set to the leader pod's HOSTNAME), `renewTime` (updated periodically by leader), and `leaseDurationSeconds: 30`.

The Lease persists after undeploy; clean it up manually if needed: `kubectl delete lease leader-example -n default`.

## Configuration
The leader election behavior can be customized through environment variables in the deployment (overrides pre-configured Lease where applicable):
- `KUBERNETES_LEADER_ELECTION`: Enable/disable leader election (default: false)
- `LEASE_NAME`: Name of the Lease resource (default: leader-example; matches pre-configured)
- `LEASE_DURATION_SECONDS`: How long the lease is valid (default: 30; pre-set in Lease spec)
- `RENEW_DEADLINE_SECONDS`: Deadline for renewing the lease (default: 20)
- `RETRY_PERIOD_MILLIS`: How often non-leaders retry to acquire leadership (default: 5000)

For production, you can pre-apply the Lease manifest separately (e.g., via Helm) before deploying Pods, ensuring it's always available.

## RBAC Requirements
The application requires specific Kubernetes permissions to perform leader election:
- **API Group**: `coordination.k8s.io`
- **Resources**: `leases`
- **Verbs**: `get`, `list`, `watch`, `create`, `update`, `patch`

These permissions are configured in the `deployment.yml` file through a Role and RoleBinding.
